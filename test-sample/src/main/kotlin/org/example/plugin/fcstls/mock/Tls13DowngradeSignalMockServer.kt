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
 * Raw-socket TLS mock server for FCS_TLSC_EXT.3.1
 * (RFC 8446 §4.1.3 — TLS 1.3 -> 1.2 downgrade signal).
 *
 * Per-connection behavior:
 *   1. Reads one ClientHello.
 *   2. Sends a crafted ServerHello:
 *        - legacy_version = 0x0303 (TLS 1.2)
 *        - random[24..31] = 44 4F 57 4E 47 52 44 01   (downgrade indicator)
 *        - session_id     = echoed from ClientHello
 *        - cipher_suite   = first TLS 1.2 suite offered by the client
 *                           (falls back to TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256)
 *        - extensions     = empty (no supported_versions, no key_share)
 *   3. Observes the client's next record. A compliant TLS 1.3 client MUST
 *      abort with an "illegal_parameter" fatal alert before continuing.
 *   4. Closes.
 *
 * No Certificate/ServerHelloDone is emitted: by RFC 8446 §4.1.3 the client
 * is required to abort on ServerHello receipt, so sending further handshake
 * messages only adds noise to the PCAP evidence.
 *
 * This server is purposely independent from DowngradeMockServer.kt
 * (BouncyCastle-based) so the two approaches can be compared side-by-side.
 */
class Tls13DowngradeSignalMockServer(private val port: Int) {

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var acceptThread: Thread? = null

    @Volatile var lastClientReaction: ClientReaction = ClientReaction.NONE
        private set

    enum class ClientReaction {
        NONE,
        FATAL_ALERT,            // Any fatal alert (level == 2)
        ILLEGAL_PARAMETER_ALERT, // The RFC-mandated response to downgrade signal
        HANDSHAKE_CONTINUED,    // TOE did NOT detect — spec violation
        CONNECTION_CLOSED,      // FIN without alert
        TIMEOUT
    }

    companion object {
        private const val RECORD_HANDSHAKE: Byte = 0x16
        private const val RECORD_ALERT: Byte = 0x15

        private const val HS_CLIENT_HELLO: Byte = 0x01
        private const val HS_SERVER_HELLO: Byte = 0x02

        private const val ALERT_LEVEL_FATAL = 2
        private const val ALERT_DESC_ILLEGAL_PARAMETER = 47

        // RFC 8446 §4.1.3: "DOWNGRD" + 0x01 => server negotiated TLS 1.2 but supports 1.3
        private val DOWNGRADE_SIGNAL_TO_TLS12 = byteArrayOf(
            0x44, 0x4F, 0x57, 0x4E, 0x47, 0x52, 0x44, 0x01
        )

        // TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 — widely supported TLS 1.2 suite.
        private val FALLBACK_CIPHER_SUITE = byteArrayOf(0xC0.toByte(), 0x2F)

        private const val HANDSHAKE_READ_TIMEOUT_MS = 5000
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket(port)
        logi("[Tls13DowngradeSignalMockServer] Listening on port $port")

        acceptThread = Thread({
            while (running.get()) {
                val sock = try {
                    serverSocket?.accept() ?: break
                } catch (e: Exception) {
                    if (running.get()) loge("[Tls13DowngradeSignalMockServer] accept failed: ${e.message}")
                    break
                }
                Thread({ handle(sock) }, "tls13-downgrade-mock-conn").also { it.isDaemon = true }.start()
            }
        }, "tls13-downgrade-mock-accept").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        logi("[Tls13DowngradeSignalMockServer] Stopped")
    }

    // ---------------------------------------------------------------- connection

    private fun handle(socket: Socket) {
        socket.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
        try {
            val din = DataInputStream(socket.getInputStream())
            val dout = DataOutputStream(socket.getOutputStream())

            val ch = readClientHello(din)
            if (ch == null) {
                logw("[Tls13DowngradeSignalMockServer] No valid ClientHello; closing")
                return
            }
            logi(
                "[Tls13DowngradeSignalMockServer] ClientHello received: " +
                    "session_id_len=${ch.sessionId.size}, " +
                    "cipher_suites=${ch.cipherSuites.size / 2}"
            )

            val sh = buildServerHelloRecord(ch)
            dout.write(sh)
            dout.flush()
            logw(
                "[Tls13DowngradeSignalMockServer] Sent TLS 1.2 ServerHello with " +
                    "DOWNGRD01 signal (${sh.size} bytes)"
            )

            lastClientReaction = observeClientReaction(din)
            logi("[Tls13DowngradeSignalMockServer] Client reaction: $lastClientReaction")
        } catch (e: Exception) {
            logi("[Tls13DowngradeSignalMockServer] Connection ended: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    // ---------------------------------------------------------------- parsing

    private data class ClientHello(
        val sessionId: ByteArray,
        val cipherSuites: ByteArray
    )

    private fun readClientHello(din: DataInputStream): ClientHello? {
        // TLS record header: type(1) + version(2) + length(2)
        val recType = din.readByte()
        din.readShort() // record version (ignored; typically 0x0301/0x0303)
        val recLen = din.readShort().toInt() and 0xFFFF
        if (recType != RECORD_HANDSHAKE) {
            logw("[Tls13DowngradeSignalMockServer] Expected Handshake record, got 0x%02x".format(recType))
            return null
        }
        val rec = ByteArray(recLen).also { din.readFully(it) }

        // Handshake header: type(1) + length(3)
        if (rec.size < 4) return null
        if (rec[0] != HS_CLIENT_HELLO) {
            logw("[Tls13DowngradeSignalMockServer] Expected ClientHello, got hs_type=0x%02x".format(rec[0]))
            return null
        }

        var off = 4
        // legacy_version(2) + random(32)
        off += 2 + 32
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

    // ---------------------------------------------------------------- serialization

    private fun buildServerHelloRecord(ch: ClientHello): ByteArray {
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        System.arraycopy(DOWNGRADE_SIGNAL_TO_TLS12, 0, random, 24, 8)

        val suite = pickTls12Suite(ch.cipherSuites)

        val body = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x03, 0x03))           // legacy_version = TLS 1.2
            write(random)                             // random with downgrade tail
            write(ch.sessionId.size)                  // session_id_length
            write(ch.sessionId)
            write(suite)                              // cipher_suite (2 bytes)
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
            write(byteArrayOf(0x03, 0x03))           // record version
            write((handshake.size ushr 8) and 0xFF)
            write(handshake.size and 0xFF)
            write(handshake)
        }.toByteArray()
    }

    /** Skip TLS 1.3-only suites (0x1301..0x1305) so the TLS 1.2 ServerHello stays plausible. */
    private fun pickTls12Suite(offered: ByteArray): ByteArray {
        var i = 0
        while (i + 1 < offered.size) {
            val hi = offered[i].toInt() and 0xFF
            if (hi != 0x13) return byteArrayOf(offered[i], offered[i + 1])
            i += 2
        }
        return FALLBACK_CIPHER_SUITE
    }

    // ---------------------------------------------------------------- observation

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
                    logi(
                        "[Tls13DowngradeSignalMockServer] Alert from client: " +
                            "level=$level desc=$desc (${alertDescName(desc)})"
                    )
                    when {
                        level == ALERT_LEVEL_FATAL && desc == ALERT_DESC_ILLEGAL_PARAMETER ->
                            ClientReaction.ILLEGAL_PARAMETER_ALERT
                        level == ALERT_LEVEL_FATAL -> ClientReaction.FATAL_ALERT
                        else -> ClientReaction.FATAL_ALERT
                    }
                }
                RECORD_HANDSHAKE -> {
                    logw(
                        "[Tls13DowngradeSignalMockServer] Client continued handshake " +
                            "despite downgrade signal — RFC 8446 §4.1.3 violation"
                    )
                    ClientReaction.HANDSHAKE_CONTINUED
                }
                else -> {
                    logi("[Tls13DowngradeSignalMockServer] Unexpected record type=0x%02x".format(t))
                    ClientReaction.FATAL_ALERT
                }
            }
        } catch (e: SocketTimeoutException) {
            logi("[Tls13DowngradeSignalMockServer] No reaction from client (timeout)")
            ClientReaction.TIMEOUT
        } catch (e: EOFException) {
            logi("[Tls13DowngradeSignalMockServer] Client closed connection (FIN) without alert")
            ClientReaction.CONNECTION_CLOSED
        }
    }

    private fun alertDescName(d: Int): String = when (d) {
        0 -> "close_notify"
        10 -> "unexpected_message"
        40 -> "handshake_failure"
        42 -> "bad_certificate"
        47 -> "illegal_parameter"
        70 -> "protocol_version"
        86 -> "inappropriate_fallback"
        else -> "other($d)"
    }
}
