package org.example.plugin.fcstls.mock

import org.example.plugin.utils.loge
import org.example.plugin.utils.logi
import org.example.plugin.utils.logw
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Raw-socket TLS mock server for POODLE-style fallback verification.
 *
 * The TOE-side guarantee being tested:
 *   - When a TLS handshake fails, the TOE does NOT blindly retry with a weaker
 *     protocol version (the behaviour that POODLE exploited).
 *   - If the TOE *does* retry with a lower version, the retry ClientHello MUST
 *     contain `TLS_FALLBACK_SCSV` (0x5600) per RFC 7507.
 *
 * Server behaviour (per TCP connection):
 *   1. Read one ClientHello.
 *   2. Record:
 *        - legacy_version
 *        - supported_versions (if extension present)
 *        - whether `TLS_FALLBACK_SCSV` (0x5600) is in cipher_suites
 *   3. Send a fatal `handshake_failure` alert to induce a fallback retry.
 *   4. Close.
 *
 * Each incoming TCP connection is pushed onto [handshakes] in arrival order.
 * The test inspects this list to determine whether the TOE fell back and,
 * if so, whether SCSV was set.
 */
class PoodleFallbackMockServer(private val port: Int) {

    /**
     * Snapshot of one ClientHello observed by the mock.
     */
    data class HandshakeRecord(
        val sequence: Int,              // 1-based arrival order
        val legacyVersion: Int,         // e.g. 0x0303 = TLS 1.2, 0x0301 = TLS 1.0
        val supportedVersions: List<Int>, // from extension 0x002B, or empty if absent
        val containsFallbackScsv: Boolean
    ) {
        fun versionLabel(v: Int): String = when (v) {
            0x0300 -> "SSL 3.0"
            0x0301 -> "TLS 1.0"
            0x0302 -> "TLS 1.1"
            0x0303 -> "TLS 1.2"
            0x0304 -> "TLS 1.3"
            else -> "0x%04x".format(v)
        }

        override fun toString(): String {
            val sv = if (supportedVersions.isEmpty()) "-"
            else supportedVersions.joinToString(",") { versionLabel(it) }
            return "seq=$sequence legacy=${versionLabel(legacyVersion)} " +
                "supported_versions=[$sv] fallback_scsv=$containsFallbackScsv"
        }
    }

    private val _handshakes: MutableList<HandshakeRecord> =
        Collections.synchronizedList(mutableListOf())

    /** Immutable snapshot for the test to inspect after stop(). */
    val handshakes: List<HandshakeRecord> get() = synchronized(_handshakes) { _handshakes.toList() }

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var acceptThread: Thread? = null
    private var sequence = 0

    companion object {
        private const val RECORD_HANDSHAKE: Byte = 0x16
        private const val RECORD_ALERT: Byte = 0x15

        private const val HS_CLIENT_HELLO: Byte = 0x01

        private const val ALERT_LEVEL_FATAL = 2
        private const val ALERT_DESC_HANDSHAKE_FAILURE = 40

        private const val EXT_SUPPORTED_VERSIONS = 0x002B

        // RFC 7507
        private const val TLS_FALLBACK_SCSV_HI = 0x56
        private const val TLS_FALLBACK_SCSV_LO = 0x00

        private const val READ_TIMEOUT_MS = 5000
    }

    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket(port)
        logi("[PoodleFallbackMockServer] Listening on port $port")

        acceptThread = Thread({
            while (running.get()) {
                val sock = try {
                    serverSocket?.accept() ?: break
                } catch (e: Exception) {
                    if (running.get()) loge("[PoodleFallbackMockServer] accept failed: ${e.message}")
                    break
                }
                Thread({ handle(sock) }, "poodle-mock-conn").also { it.isDaemon = true }.start()
            }
        }, "poodle-mock-accept").also {
            it.isDaemon = true
            it.start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        acceptThread?.interrupt()
        logi(
            "[PoodleFallbackMockServer] Stopped — observed ${_handshakes.size} " +
                "ClientHello(s): " + handshakes.joinToString(" | ") { it.toString() }
        )
    }

    // ---------------------------------------------------------------- per-connection

    private fun handle(socket: Socket) {
        socket.soTimeout = READ_TIMEOUT_MS
        try {
            val din = DataInputStream(socket.getInputStream())
            val dout = DataOutputStream(socket.getOutputStream())

            val record = readClientHello(din)
            if (record == null) {
                logw("[PoodleFallbackMockServer] No valid ClientHello on connection")
                return
            }
            val seq = synchronized(this) { ++sequence }
            val entry = record.copy(sequence = seq)
            _handshakes.add(entry)
            logi("[PoodleFallbackMockServer] Recorded $entry")

            // Induce a fallback attempt.
            dout.write(fatalHandshakeFailureAlert())
            dout.flush()
            logw("[PoodleFallbackMockServer] Sent fatal handshake_failure alert (seq=$seq)")
        } catch (e: SocketTimeoutException) {
            logi("[PoodleFallbackMockServer] Read timeout")
        } catch (e: EOFException) {
            logi("[PoodleFallbackMockServer] Connection closed by peer before ClientHello")
        } catch (e: Exception) {
            logi("[PoodleFallbackMockServer] Connection error: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    // ---------------------------------------------------------------- parsing

    private fun readClientHello(din: DataInputStream): HandshakeRecord? {
        val recType = din.readByte()
        din.readShort() // record version
        val recLen = din.readShort().toInt() and 0xFFFF
        if (recType != RECORD_HANDSHAKE) return null
        val rec = ByteArray(recLen).also { din.readFully(it) }
        if (rec.size < 4 || rec[0] != HS_CLIENT_HELLO) return null

        var off = 4
        if (off + 2 + 32 > rec.size) return null
        val legacyVersion = ((rec[off].toInt() and 0xFF) shl 8) or (rec[off + 1].toInt() and 0xFF)
        off += 2 + 32

        val sidLen = rec[off].toInt() and 0xFF; off += 1
        off += sidLen

        if (off + 2 > rec.size) return null
        val csLen = ((rec[off].toInt() and 0xFF) shl 8) or (rec[off + 1].toInt() and 0xFF)
        off += 2
        if (off + csLen > rec.size) return null
        val cipherEnd = off + csLen

        var scsvFound = false
        var i = off
        while (i + 1 < cipherEnd) {
            val hi = rec[i].toInt() and 0xFF
            val lo = rec[i + 1].toInt() and 0xFF
            if (hi == TLS_FALLBACK_SCSV_HI && lo == TLS_FALLBACK_SCSV_LO) {
                scsvFound = true
                break
            }
            i += 2
        }
        off = cipherEnd

        if (off + 1 > rec.size) {
            return HandshakeRecord(0, legacyVersion, emptyList(), scsvFound)
        }
        val cmLen = rec[off].toInt() and 0xFF
        off += 1 + cmLen

        val supportedVersions = mutableListOf<Int>()
        if (off + 2 <= rec.size) {
            val extLen = ((rec[off].toInt() and 0xFF) shl 8) or (rec[off + 1].toInt() and 0xFF)
            off += 2
            val extEnd = minOf(off + extLen, rec.size)
            while (off + 4 <= extEnd) {
                val extType = ((rec[off].toInt() and 0xFF) shl 8) or (rec[off + 1].toInt() and 0xFF)
                val extDataLen = ((rec[off + 2].toInt() and 0xFF) shl 8) or (rec[off + 3].toInt() and 0xFF)
                off += 4
                if (off + extDataLen > extEnd) break
                if (extType == EXT_SUPPORTED_VERSIONS && extDataLen >= 1) {
                    // ClientHello format: list length (1 byte) + N * 2 bytes.
                    val listLen = rec[off].toInt() and 0xFF
                    var p = off + 1
                    val listEnd = minOf(p + listLen, off + extDataLen)
                    while (p + 1 < listEnd) {
                        val v = ((rec[p].toInt() and 0xFF) shl 8) or (rec[p + 1].toInt() and 0xFF)
                        supportedVersions.add(v)
                        p += 2
                    }
                }
                off += extDataLen
            }
        }

        return HandshakeRecord(0, legacyVersion, supportedVersions, scsvFound)
    }

    // ---------------------------------------------------------------- serialization

    private fun fatalHandshakeFailureAlert(): ByteArray =
        byteArrayOf(
            RECORD_ALERT,
            0x03, 0x03,                                 // record version TLS 1.2
            0x00, 0x02,                                 // length
            ALERT_LEVEL_FATAL.toByte(),                 // level = fatal
            ALERT_DESC_HANDSHAKE_FAILURE.toByte()       // description
        )
}
