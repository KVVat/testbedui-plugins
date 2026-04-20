package org.example.plugin.fcstls.mock

/**
 * A mock TLS server to verify FCS_TLSC_EXT.4 (Secure Renegotiation).
 * This server should be able to initiate an insecure renegotiation request
 * after the initial handshake to verify that the client rejects it.
 */
class RenegotiationMockServer(private val port: Int) {

    fun start() {
        // TODO: Implement server that can trigger renegotiation
        println("[RenegotiationMockServer] Started on port $port (Skeleton)")
    }

    fun stop() {
        println("[RenegotiationMockServer] Stopped")
    }
    
    fun triggerInsecureRenegotiation() {
        // TODO: Send hello request or equivalent to trigger renegotiation
        println("[RenegotiationMockServer] Triggering insecure renegotiation...")
    }
}
