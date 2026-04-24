package org.example.plugin.utils

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * TLS parameters lookup utility.
 * Hardcoded maps are used to avoid ZipException when loading CSV from JAR.
 */
object TlsTableLookup {
    // Hardcoded maps for major ciphers, groups and extensions
    private val cipherSuites = mapOf(
        0x1301 to "TLS_AES_128_GCM_SHA256",
        0x1302 to "TLS_AES_256_GCM_SHA384",
        0x1303 to "TLS_CHACHA20_POLY1305_SHA256",
        0xC02B to "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        0xC02F to "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        0xC02C to "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
        0xC030 to "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        0xC009 to "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
        0xC013 to "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        0x009C to "TLS_RSA_WITH_AES_128_GCM_SHA256",
        0x0035 to "TLS_RSA_WITH_AES_256_CBC_SHA"
    )

    private val supportedGroups = mapOf(
        0x0017 to "secp256r1",
        0x0018 to "secp384r1",
        0x001D to "x25519",
        0x001E to "x448"
    )

    private val signatureAlgorithms = mapOf(
        0x0401 to "rsa_pkcs1_sha256",
        0x0501 to "rsa_pkcs1_sha384",
        0x0601 to "rsa_pkcs1_sha512",
        0x0403 to "ecdsa_secp256r1_sha256",
        0x0503 to "ecdsa_secp384r1_sha384",
        0x0804 to "rsa_pss_rsae_sha256",
        0x0805 to "rsa_pss_rsae_sha384",
        0x0806 to "rsa_pss_rsae_sha512",
        0x0201 to "rsa_pkcs1_sha1"
    )

    private val extensionTypes = mapOf(
        0x0000 to "server_name",
        0x0005 to "status_request",
        0x000A to "supported_groups",
        0x000B to "ec_point_formats",
        0x000D to "signature_algorithms",
        0x0010 to "application_layer_protocol_negotiation",
        0x0015 to "padding",
        0x0017 to "extended_master_secret",
        0x0023 to "session_ticket",
        0x002B to "supported_versions",
        0x002D to "psk_key_exchange_modes",
        0x0033 to "key_share",
        0xFF01 to "renegotiation_info"
    )

    /**
     * Get algorithm name by code and source index.
     * Fallback to candidate name since we removed multi-source CSV.
     */
    fun getAlgorithmName(code: Int, sourceIndex: Int): String {
        return getCandidateName(code)
    }

    /**
     * Get a candidate name for a given code.
     * @param code The integer code of the cipher suite (e.g., 0x1301)
     * @return The available name, or hex code if not found.
     */
    fun getCandidateName(code: Int): String {
        return cipherSuites[code] ?: String.format("0x%04X", code)
    }

    fun getGroupName(code: Int): String = supportedGroups[code] ?: String.format("0x%04X", code)
    fun getSignatureSchemeName(code: Int): String = signatureAlgorithms[code] ?: String.format("0x%04X", code)
    fun getExtensionName(code: Int): String = extensionTypes[code] ?: String.format("0x%04X", code)
}
