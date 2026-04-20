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
 * Raw-socket TLS mock server for FCS_TLSC_EXT.1.1 (Active Rejection of Legacy Protocols).
 *
 * Per-connection behavior:
 *   1. Reads one ClientHello.
 *   2. Sends a crafted ServerHello forcing SSLv3:
 *        - legacy_version = 0x0300 (SSLv3)
 *        - random         = normal random
 *        - session_id     = echoed from ClientHello
 *        - cipher_suite   = a standard suite (or fallback)
 *        - extensions     = empty
 *   3. Observes the client's next record. A compliant client MUST
 *      abort with a protocol version alert or similar.
 *   4. Closes.
 */
class TlsLegacyRejectMockServer(private val port: Int) {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var acceptThread: Thread? = null

    @Volatile var lastClientReaction: ClientReaction = ClientReaction.NONE
        private set

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

        private val FALLBACK_CIPHER_SUITE = byteArrayOf(0x00.toByte(), 0x2F) // TLS_RSA_WITH_AES_128_CBC_SHA (SSLv3 supported)

        private const val HANDSHAKE_READ_TIMEOUT_MS = 5000
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket(port)
        logi("[TlsLegacyRejectMockServer] Listening on port $port")

        acceptThread = Thread({
            while (running.get()) {
                val sock = try {
                    serverSocket?.accept() ?: break
                } catch (e: Exception) {
                    if (running.get()) loge("[TlsLegacyRejectMockServer] accept failed: ${e.message}")
                    break
                }
                Thread({ handle(sock) }, "tls-legacy-reject-mock-conn").also { it.isDaemon = true }.start()
            }
        }, "tls-legacy-reject-mock-accept").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        logi("[TlsLegacyRejectMockServer] Stopped")
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
        try {
            val din = DataInputStream(socket.getInputStream())
            val dout = DataOutputStream(socket.getOutputStream())

            val ch = readClientHello(din)
            if (ch == null) {
                logw("[TlsLegacyRejectMockServer] No valid ClientHello; closing")
                return
            }
            logi("[TlsLegacyRejectMockServer] ClientHello received")

            val sh = buildServerHelloRecord(ch)
            dout.write(sh)
            dout.flush()
            logw("[TlsLegacyRejectMockServer] Sent SSLv3 ServerHello")

            lastClientReaction = observeClientReaction(din)
            logi("[TlsLegacyRejectMockServer] Client reaction: $lastClientReaction")
        } catch (e: Exception) {
            logi("[TlsLegacyRejectMockServer] Connection ended: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
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
        SecureRandom().nextBytes(random)

        val body = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x03, 0x00))           // legacy_version = SSLv3 (0x0300)
            write(random)
            write(ch.sessionId.size)
            write(ch.sessionId)
            write(FALLBACK_CIPHER_SUITE)             // Use a suite compatible with SSLv3
            write(0x00)                               // compression_method = null
            write(byteArrayOf(0x00, 0x00))           // extensions length = 0
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
            write(byteArrayOf(0x03, 0x00))           // record version SSLv3
            write((handshake.size ushr 8) and 0xFF)
            write(handshake.size and 0xFF)
            write(handshake)
        }.toByteArray()
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
                    logi("[TlsLegacyRejectMockServer] Alert from client: level=$level desc=$desc")
                    if (level == 2) ClientReaction.FATAL_ALERT else ClientReaction.FATAL_ALERT
                }
                RECORD_HANDSHAKE -> {
                    logw("[TlsLegacyRejectMockServer] Client continued handshake")
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
