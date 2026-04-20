package org.example.plugin.fcstls.mock

import org.bouncycastle.tls.DefaultTlsServer
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.TlsServerProtocol
import org.bouncycastle.tls.TlsCredentials
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import java.io.File
import java.io.FileInputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
// import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredential
import org.example.plugin.utils.logi
import org.example.plugin.utils.logw
import org.example.plugin.utils.loge

/**
 * A mock TLS server to verify FCS_TLSC_EXT.3.1 (Downgrade Protection).
 * Scenario: First connection succeeds as TLS 1.3.
 * Second connection (Resumption) is forced to downgrade to TLS 1.2 with a signal.
 */
class DowngradeMockServer(private val port: Int, private val p12Path: String, private val p12Pass: String) {

    private var serverSocket: ServerSocket? = null
    private var running = false
    private var thread: Thread? = null
    
    private var connectionCount = 0

    fun start() {
        running = true
        serverSocket = ServerSocket(port)
        logi("[DowngradeMockServer] Listening on port $port")

        thread = Thread {
            while (running) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    handleConnection(socket)
                } catch (e: Exception) {
                    if (running) {
                        loge("[DowngradeMockServer] Error accepting connection: ${e.message}")
                    }
                }
            }
        }
        thread?.start()
    }

    private fun handleConnection(socket: Socket) {
        Thread {
            try {
                val isResumptionAttempt = connectionCount > 0
                connectionCount++
                
                logi("[DowngradeMockServer] Connection #$connectionCount from ${socket.remoteSocketAddress}")
                
                val customRandom = object : SecureRandom() {
                    override fun nextBytes(bytes: ByteArray) {
                        super.nextBytes(bytes)
                        if (isResumptionAttempt && bytes.size == 32) {
                            bytes[24] = 0x44.toByte()
                            bytes[25] = 0x4F.toByte()
                            bytes[26] = 0x57.toByte()
                            bytes[27] = 0x4E.toByte()
                            bytes[28] = 0x47.toByte()
                            bytes[29] = 0x52.toByte()
                            bytes[30] = 0x44.toByte()
                            bytes[31] = 0x01.toByte()
                            logw("[DowngradeMockServer] Injected TLS 1.3 downgrade signal into Server Random")
                        }
                    }
                }
                
                val crypto = BcTlsCrypto(customRandom)
                val protocol = TlsServerProtocol(socket.getInputStream(), socket.getOutputStream())
                
                // Load credentials from P12 file
                val ks = KeyStore.getInstance("PKCS12")
                ks.load(FileInputStream(p12Path), p12Pass.toCharArray())
                val alias = ks.aliases().nextElement()
                val privateKey = ks.getKey(alias, p12Pass.toCharArray()) as PrivateKey
                val certChain = ks.getCertificateChain(alias)
                
                // Map to Bouncy Castle types (Simplified/Best effort)
                // Note: This might fail if BcDefaultTlsCredential is not the right class or signature changed.
                // We try to use it to resolve the "credentials unhandled" error.
                
                val server = object : DefaultTlsServer(crypto) {
                    override fun getProtocolVersions(): Array<ProtocolVersion> {
                        return if (isResumptionAttempt) {
                            logi("[DowngradeMockServer] Forcing TLS 1.2 for resumption")
                            arrayOf(ProtocolVersion.TLSv12)
                        } else {
                            logi("[DowngradeMockServer] Allowing TLS 1.3 for initial connection")
                            arrayOf(ProtocolVersion.TLSv13, ProtocolVersion.TLSv12)
                        }
                    }
                    
                    // Implement this if possible or rely on default if we can set it elsewhere
                    // Bouncy Castle DefaultTlsServer usually handles it if we override getCredentials()
                    // or if we pass a configured crypto.
                }
                
                protocol.accept(server)
                logi("[DowngradeMockServer] Handshake completed")
                
                socket.close()
            } catch (e: Exception) {
                logi("[DowngradeMockServer] Handshake failed: ${e.message}")
                try { socket.close() } catch (ioe: Exception) {}
            }
        }.start()
    }

    fun stop() {
        running = false
        serverSocket?.close()
        thread?.interrupt()
        logi("[DowngradeMockServer] Stopped")
    }
}
