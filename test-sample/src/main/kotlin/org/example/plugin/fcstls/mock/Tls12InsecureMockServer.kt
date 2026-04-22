package org.example.plugin.fcstls.mock

import org.example.plugin.utils.logi
import org.example.plugin.utils.logw
import org.example.plugin.utils.loge
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Raw-socket TLS mock server for FCS_TLSC_EXT.4 (Secure Renegotiation Rejection).
 *
 * Per-connection behavior:
 *   1. Reads one ClientHello.
 *   2. Sends a crafted ServerHello:
 *        - legacy_version = 0x0303 (TLS 1.2)
 *        - random         = normal random (no downgrade signal)
 *        - session_id     = echoed from ClientHello
 *        - cipher_suite   = first TLS 1.2 suite offered by the client
 *        - extensions     = empty (CRITICAL: no renegotiation_info extension)
 *   3. Observes the client's next record. If the client requires secure
 *      renegotiation support from the server (RFC 5746), it should abort
 *      or refuse to continue.
 *   4. Closes.
 */
class Tls12InsecureMockServer(private val port: Int) {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var acceptThread: Thread? = null

    @Volatile var lastClientReaction: ClientReaction = ClientReaction.NONE
        private set

    private val serverCertBytes: ByteArray by lazy {
        val certFile = java.io.File(org.example.project.JUnitBridge.resourceDir, "wildcard-rsa2048.crt")
        val cf = java.security.cert.CertificateFactory.getInstance("X.509")
        java.io.FileInputStream(certFile).use { fis ->
            val cert = cf.generateCertificate(fis) as java.security.cert.X509Certificate
            cert.encoded
        }
    }

    enum class ClientReaction {
        NONE,
        FATAL_ALERT,
        HANDSHAKE_CONTINUED,
        CONNECTION_CLOSED,
        TIMEOUT
    }
    
    companion object {
        private const val RECORD_HANDSHAKE: Byte = 0x16
        private const val RECORD_ALERT: Byte = 0x15
        
        private const val HS_CLIENT_HELLO: Byte = 0x01
        private const val HS_SERVER_HELLO: Byte = 0x02
        private const val HS_CERTIFICATE: Byte = 0x0b
        private const val HS_SERVER_HELLO_DONE: Byte = 0x0e

        private val FALLBACK_CIPHER_SUITE = byteArrayOf(0xC0.toByte(), 0x2F)

        private const val HANDSHAKE_READ_TIMEOUT_MS = 5000
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket(port)
        logi("[Tls12InsecureMockServer] Listening on port $port")

        acceptThread = Thread({
            while (running.get()) {
                val sock = try {
                    serverSocket?.accept() ?: break
                } catch (e: Exception) {
                    if (running.get()) loge("[Tls12InsecureMockServer] accept failed: ${e.message}")
                    break
                }
                Thread({ handle(sock) }, "tls12-insecure-mock-conn").also { it.isDaemon = true }.start()
            }
        }, "tls12-insecure-mock-accept").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        logi("[Tls12InsecureMockServer] Stopped")
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
        try {
            val din = DataInputStream(socket.getInputStream())
            val dout = DataOutputStream(socket.getOutputStream())

            val ch = readClientHello(din)
            if (ch == null) {
                logw("[Tls12InsecureMockServer] No valid ClientHello; closing")
                return
            }
            logi("[Tls12InsecureMockServer] ClientHello received")

            val sh = buildServerHelloRecord(ch)
            dout.write(sh)
            
            // Send Certificate
            val certRecord = buildCertificateRecord()
            dout.write(certRecord)
            
            // Send ServerHelloDone
            val shdRecord = buildServerHelloDoneRecord()
            dout.write(shdRecord)
            
            dout.flush()
            logw("[Tls12InsecureMockServer] Sent TLS 1.2 ServerHello, Cert, SHDone without renegotiation_info")

            lastClientReaction = observeClientReaction(din)
            logi("[Tls12InsecureMockServer] Client reaction: $lastClientReaction")
        } catch (e: Exception) {
            logi("[Tls12InsecureMockServer] Connection ended: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun buildCertificateRecord(): ByteArray {
        val certBytes = serverCertBytes
        val body = ByteArrayOutputStream().apply {
            // Certificate List Length (3 bytes)
            val listLen = certBytes.size + 3
            write((listLen ushr 16) and 0xFF)
            write((listLen ushr 8) and 0xFF)
            write(listLen and 0xFF)
            
            // Certificate Length (3 bytes)
            val cLen = certBytes.size
            write((cLen ushr 16) and 0xFF)
            write((cLen ushr 8) and 0xFF)
            write(cLen and 0xFF)
            
            // Certificate Data
            write(certBytes)
        }.toByteArray()

        val handshake = ByteArrayOutputStream().apply {
            write(HS_CERTIFICATE.toInt())
            write((body.size ushr 16) and 0xFF)
            write((body.size ushr 8) and 0xFF)
            write(body.size and 0xFF)
            write(body)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write(RECORD_HANDSHAKE.toInt())
            write(byteArrayOf(0x03, 0x03))           // record version
            write((handshake.size ushr 8) and 0xFF)
            write(handshake.size and 0xFF)
            write(handshake)
        }.toByteArray()
    }

    private fun buildServerHelloDoneRecord(): ByteArray {
        val handshake = ByteArrayOutputStream().apply {
            write(HS_SERVER_HELLO_DONE.toInt())
            write(0)
            write(0)
            write(0)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write(RECORD_HANDSHAKE.toInt())
            write(byteArrayOf(0x03, 0x03))           // record version
            write((handshake.size ushr 8) and 0xFF)
            write(handshake.size and 0xFF)
            write(handshake)
        }.toByteArray()
    }

    private data class ClientHello(
        val sessionId: ByteArray,
        val cipherSuites: ByteArray
    )

    private fun readClientHello(din: DataInputStream): ClientHello? {
        val recType = din.readByte()
        din.readShort() // version
        val recLen = din.readShort().toInt() and 0xFFFF
        if (recType != RECORD_HANDSHAKE) return null
        val rec = ByteArray(recLen).also { din.readFully(it) }

        if (rec.size < 4) return null
        if (rec[0] != HS_CLIENT_HELLO) return null

        var off = 4
        off += 2 + 32 // version + random
        if (off + 1 > rec.size) return null
        val sidLen = rec[off].toInt() and 0xFF
        off += 1
        if (off + sidLen + 2 > rec.size) return null
        val sessionId = rec.copyOfRange(off, off + sidLen)
        off += sidLen

        val csLen = ((rec[off].toInt() and 0xFF) shl 8) or (rec[off + 1].toInt() and 0xFF)
        off += 2
        if (off + csLen > rec.size) return null
        val cipherSuites = rec.copyOfRange(off, off + csLen)
        return ClientHello(sessionId, cipherSuites)
    }

    private fun buildServerHelloRecord(ch: ClientHello): ByteArray {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random) // Normal random, no downgrade signal

        val suite = pickTls12Suite(ch.cipherSuites)

        val body = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x03, 0x03))           // legacy_version = TLS 1.2
            write(random)
            write(0) // Empty session ID to avoid 'SERVER_ECHOED_INVALID_SESSION_ID'
            write(suite)
            write(0x00)                               // compression_method = null
            write(byteArrayOf(0x00, 0x00))           // extensions length = 0 (No secure renegotiation)
        }.toByteArray()

        val handshake = ByteArrayOutputStream().apply {
            write(HS_SERVER_HELLO.toInt())
            write((body.size ushr 16) and 0xFF)
            write((body.size ushr 8) and 0xFF)
            write(body.size and 0xFF)
            write(body)
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write(RECORD_HANDSHAKE.toInt())
            write(byteArrayOf(0x03, 0x03))           // record version
            write((handshake.size ushr 8) and 0xFF)
            write(handshake.size and 0xFF)
            write(handshake)
        }.toByteArray()
    }

    private fun pickTls12Suite(offered: ByteArray): ByteArray {
        var i = 0
        while (i + 1 < offered.size) {
            val hi = offered[i].toInt() and 0xFF
            if (hi != 0x13) return byteArrayOf(offered[i], offered[i + 1])
            i += 2
        }
        return FALLBACK_CIPHER_SUITE
    }

    private fun observeClientReaction(din: DataInputStream): ClientReaction {
        return try {
            val t = din.readByte()
            din.readShort() // version
            val len = din.readShort().toInt() and 0xFFFF
            val payload = ByteArray(len).also { din.readFully(it) }

            when (t) {
                RECORD_ALERT -> {
                    val level = payload.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
                    val desc = payload.getOrNull(1)?.toInt()?.and(0xFF) ?: -1
                    logi("[Tls12InsecureMockServer] Alert from client: level=$level desc=$desc")
                    if (level == 2) ClientReaction.FATAL_ALERT else ClientReaction.FATAL_ALERT
                }
                RECORD_HANDSHAKE -> {
                    logw("[Tls12InsecureMockServer] Client continued handshake")
                    ClientReaction.HANDSHAKE_CONTINUED
                }
                else -> ClientReaction.FATAL_ALERT
            }
        } catch (e: SocketTimeoutException) {
            ClientReaction.TIMEOUT
        } catch (e: EOFException) {
            ClientReaction.CONNECTION_CLOSED
        }
    }
}
