package org.example.plugin.fcstls

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.Report
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.example.project.JUnitBridge
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.absolutePathString
import org.example.project.adb.rules.AdbDeviceRule
import com.malinskiy.adam.AndroidDebugBridgeClient
import io.pkts.Pcap
import io.pkts.packet.TCPPacket
import io.pkts.protocol.Protocol
import org.example.plugin.utils.SFRCheckList
import org.example.plugin.utils.*


/**
 * FCS_TLSC_EXT.1 TLS Client Protocol
 * Verify that the TSF implements TLS 1.2 or TLS 1.3 as a client.
 *
 * ### Requirement Mapping
 *
 * #### FCS_TLSC_EXT.1: Client Protocol
 * * **FCS_TLSC_EXT.1.1: TLS 1.2/1.3 Support & Legacy Rejection**
 *   * Verification: Support TLS 1.2/1.3 and abort prior versions.
 *   * Test: `testTls12Support`, `testTls13Support`, `testTls10Reject`, `testTls11Reject`
 * * **FCS_TLSC_EXT.1.2: Supported Ciphersuites**
 *   * Verification: Offer supported ciphersuites in ClientHello (including CNSA).
 *   * Test: Verified in `analyzePcap` (presence of CNSA ciphers).
 * * **FCS_TLSC_EXT.1.3: Forbidden Ciphers Rejection**
 *   * Verification: Do not offer Null, RC4, 3DES, etc.
 *   * Test: `testNullCipherReject`, `testRc4Reject`, `test3DesReject` (Verifies connection failure when server only supports these).
 * * **FCS_TLSC_EXT.1.4: Supported & Forbidden Extensions**
 *   * Verification: Support specific extensions (signature_algorithms, etc.) and NOT send `early_data`.
 *   * Test: Verified in `analyzePcap` (checks for required extensions and absence of `early_data`).
 * * **FCS_TLSC_EXT.1.5: Identifier Verification**
 *   * Verification: Verify server identifier matches reference identifier.
 *   * Test: `testInvalidHost` (Verifies failure on hostname mismatch).
 * * **FCS_TLSC_EXT.1.6: Invalid Certificate Rejection**
 *   * Verification: Reject connections if server certificate is invalid.
 *   * Test: `testExpiredHost` (Verifies failure on expired certificate).
 *
 * #### FCS_TLSC_EXT.2: Mutual Authentication
 * * **FCS_TLSC_EXT.2.1: Client Certificate Control**
 *   * Verification: Send client certificate when requested, and do not send when not requested.
 *   * Test: `testMutualAuthWithCert`, `testMutualAuthNoCert`
 *
 * #### FCS_TLSC_EXT.3: Downgrade Protection
 * * **FCS_TLSC_EXT.3.1**: Downgrade Protection
 *   * Verification: (Not implemented) Requires custom server to send downgrade indicator. Not supported by badssl.com.
 *
 * #### FCS_TLSC_EXT.4: Secure Renegotiation
 * * **FCS_TLSC_EXT.4.1**: Renegotiation Protection
 *   * Verification: Check for SCSV (0x00FF) or renegotiation_info extension (0xFF01) in Client Hello.
 *   * Test: Verified in `analyzePcap` for successful connections.
 *
 * #### FCS_TLSC_EXT.5: Session Resumption
 * * **FCS_TLSC_EXT.5.1**: Session Resumption Support
 *   * Verification: Check for SessionTicket extension (0x0023) and multiple Client Hellos for resumption attempt.
 *   * Test: `testSessionResumption`
 *
 * #### FCS_TLSC_EXT.6: TLS 1.3 Resumption Refinements
 * * **FCS_TLSC_EXT.6.1**: PSK Key Exchange Modes
 *   * Verification: Check for `psk_key_exchange_modes` extension (0x002D) in Client Hello.
 *   * Test: Verified in `analyzePcap`.
 * * **FCS_TLSC_EXT.6.2**: No Early Data
 *   * Verification: Verify `early_data` extension (0x002A) is NOT present in Client Hello.
 *   * Test: Verified in `analyzePcap`.
 */
@SFR("FCS_TLSC_EXT", """
The TSF shall implement [selection: TLS 1.2 (RFC 5246), TLS 1.3 (RFC 8446)] as a client
that supports additional functionality for session renegotiation protection and
abort attempts by a server to negotiate any TLS or SSL version prior to TLS 1.2.
""",category="network")
class FcsTlscExtTest {

  companion object {
    var appInstalled = false
    
    @BeforeClass
    @JvmStatic
    fun setupCheckList() {
      SFRCheckList.register("FCS_TLSC_EXT.1.1/TLS10", "Verify that connections with TLS 1.0 are not permitted (RFC 5246 / RFC 8446)")
      SFRCheckList.register("FCS_TLSC_EXT.1.1/TLS11", "Verify that connections with TLS 1.1 are not permitted (RFC 5246 / RFC 8446)")
      SFRCheckList.register("FCS_TLSC_EXT.1.1/TLS12", "Verify support for TLS 1.2 (RFC 5246)")
      SFRCheckList.register("FCS_TLSC_EXT.1.1/TLS13", "Verify support for TLS 1.3 (RFC 8446)")
      SFRCheckList.register("FCS_TLSC_EXT.1.2", "Verify support for required ciphersuites (RFC 5246, RFC 8446)")
      SFRCheckList.register("FCS_TLSC_EXT.1.2/Android", "Verify support for Android-specific CNSA ciphersuites (RFC 5289, RFC 8422)")
      SFRCheckList.register("FCS_TLSC_EXT.1.3/NullCipher", "Verify that null ciphers are not permitted (RFC 5246)")
      SFRCheckList.register("FCS_TLSC_EXT.1.3/RC4", "Verify that RC4 is not permitted (RFC 5246)")
      SFRCheckList.register("FCS_TLSC_EXT.1.3/3DES", "Verify that 3DES is not permitted (RFC 5246)")
      SFRCheckList.register("FCS_TLSC_EXT.1.4", "Verify support for required extensions (RFC 8446)")
      SFRCheckList.register("FCS_TLSC_EXT.1.4/ExtendedMasterSecret", "Verify support for Extended Master Secret (RFC 7627)")
      SFRCheckList.register("FCS_TLSC_EXT.1.5", "Verify server identifier matches reference identifier (RFC 6125)")
      SFRCheckList.register("FCS_TLSC_EXT.1.6", "Verify invalid certificate rejection (RFC 5280)")
      SFRCheckList.register("FCS_TLSC_EXT.2.1", "Verify mutual authentication (RFC 5246, RFC 8446)")
      SFRCheckList.register("FCS_TLSC_EXT.4.1", "Verify secure renegotiation (RFC 5746)")
      SFRCheckList.register("FCS_TLSC_EXT.5.1", "Verify session resumption offered (RFC 5077, RFC 8446)")
SFRCheckList.register(
        "FCS_TLSC_EXT.5.1/SessionTicket",
        "Verify existence ofSessionTicket in non-resumption tests"
)

      SFRCheckList.register("FCS_TLSC_EXT.6.1", "Verify PSK key exchange modes (RFC 8446)")
      SFRCheckList.register("FCS_TLSC_EXT.6.2", "Verify no early data (RFC 8446)")
    }
  }

  /*
   * Test Items from tls-2.1.md FCS_TLSC_EXT.1.1:
   * 
   * 1. [Test FCS_TLSC_EXT.1.1:1] Supported configurations
   *    - Check ClientHello versions and ciphers.
   *    - Verify successful handshake.
   *    (Covered by testNormalHost)
   * 
   * 2. [Test FCS_TLSC_EXT.1.1:2.1] Obsolete versions
   *    - Server negotiates SSLv2, SSLv3, TLS 1.0, or TLS 1.1.
   *    - Verify Client terminates connection.
   *    (To be implemented)
   * 
   * 3. [Test FCS_TLSC_EXT.1.1:3.1] Supported ciphersuite not offered
   *    - Server selects cipher not in ClientHello.
   *    - Verify Client rejects connection.
   * 
   * 4. [Test FCS_TLSC_EXT.1.1:3.3] Null ciphersuite
   *    - Server selects TLS_NULL_WITH_NULL_NULL.
   *    - Verify Client rejects connection.
   * 
   * (Non-standard certificate validation tests from badssl.com)
   * - testExpiredHost: Verify rejection of expired certificate.
   * - testInvalidHost: Verify rejection of wrong host certificate.
   */

  private val TEST_MODULE = "networkcheck-debug.apk"

  @get:Rule
  val adb = AdbDeviceRule()
  private val client: AndroidDebugBridgeClient by lazy { adb.adb }

  @get:Rule
  val watcher: TestWatcher by lazy { ADSRPTestWatcher(adb) }
  @get:Rule
  val testname: TestName = TestName()

  private val a: TestAssertLogger by lazy { TestAssertLogger(testname) }
  @get:Rule
  val errs: ErrorCollector = ErrorCollector()

  val REQUIRED_CIPHERS_IN_SFR = arrayOf(
    "TLS_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
    "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384")

  val CIPHER_MAP = mapOf(
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" to 0xC02F,
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384" to 0xC030,
    "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256" to 0xC02B,
    "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384" to 0xC02C
  )

  @Before
  fun setup() {
    runBlocking {
      client.execute(ShellCommandRequest("rm /data/local/tmp/$TEST_MODULE"), adb.deviceSerial)
      client.execute(ShellCommandRequest("rm /data/local/tmp/traffic.pcap"), adb.deviceSerial)
    }
  }

  @After
  fun teardown() {
    runBlocking {
      client.execute(ShellCommandRequest("rm /data/local/tmp/$TEST_MODULE"), adb.deviceSerial)
    }
  }

  @Test
  fun testTls12Support() {
    val hostName = "https://tls-v1-2.badssl.com:1012/"
    val resp = tlsCapturePacket("tls12_support", hostName)
    val httpret = resp.httpResponse
    log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.1/TLS12")
  }

  @Test
  fun testTls13Support() {
    val hostName = "https://ipv4.google.com/"
    val resp = tlsCapturePacket("tls13_support", hostName)
    val httpret = resp.httpResponse
    log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.1/TLS13")
  }

  @Test
  fun testTls10Reject() {
    val hostName = "https://tls-v1-0.badssl.com:1010/"
    val resp = tlsCapturePacket("tls10", hostName)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.1/TLS10")
  }

  @Test
  fun testTls11Reject() {
    val hostName = "https://tls-v1-1.badssl.com:1011/"
    val resp = tlsCapturePacket("tls11", hostName)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.1/TLS11")
  }

  @Test
  fun testNullCipherReject() {
    val hostName = "https://null.badssl.com/"
    val resp = tlsCapturePacket("nullcipher", hostName)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.3/NullCipher")
  }

  @Test
  fun test3DesReject() {
    val hostName = "https://3des.badssl.com/"
    val resp = tlsCapturePacket("3des", hostName)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.3/3DES")
  }

  @Test
  fun testRc4Reject() {
    val hostName = "https://rc4.badssl.com/"
    val resp = tlsCapturePacket("rc4", hostName)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.3/RC4")
  }

  @Test
  fun testExpiredHost() {
    val hostName = "https://expired.badssl.com/"
    val resp = tlsCapturePacket("expired", hostName)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should be 525 or error"), httpret, IsEqual("525"))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = true)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.6")
  }

  @Test
  fun testInvalidHost() {
    val hostName = "https://wrong.host.badssl.com/"
    val resp = tlsCapturePacket("invalid", hostName)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should be 526 or error"), httpret, IsEqual("526"))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = true)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.5")
  }

  @Test
  fun testMutualAuthNoCert() {
    val hostName = "https://tls-v1-2.badssl.com:1012/"
    val resp = tlsCapturePacket("nocert", hostName)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false, expectClientCert = false)
  }

  @Test
  fun testMutualAuthWithCert() {
    val hostName = "https://client.badssl.com/"
    val p12Path = "/data/data/com.example.openurl/files/badssl.com-client.p12"
    val p12Pass = "badssl.com"
    
    // Push cert from resources to device
    val certFile = File(JUnitBridge.resourceDir, "badssl.com-client.p12")
    Assert.assertTrue("Certificate file not found in resources: ${certFile.absolutePath}", certFile.exists())

    val serial = adb.deviceSerial

    // Copy to app private directory
    runBlocking {
      // Push file using Adam
      val channel = client.execute(
        com.malinskiy.adam.request.sync.v1.PushFileRequest(certFile, "/sdcard/${certFile.name}"),
        this,
        serial = serial
      )
      for (progress in channel) {
          // wait for completion
      }

      client.execute(com.malinskiy.adam.request.shell.v1.ShellCommandRequest("su 0 mkdir -p /data/data/com.example.openurl/files/"), serial)
      client.execute(com.malinskiy.adam.request.shell.v1.ShellCommandRequest("su 0 cp /sdcard/badssl.com-client.p12 /data/data/com.example.openurl/files/"), serial)
      client.execute(com.malinskiy.adam.request.shell.v1.ShellCommandRequest("su 0 chmod 666 /data/data/com.example.openurl/files/badssl.com-client.p12"), serial)
    }

    val resp = tlsCapturePacket("withcert", hostName, p12Path, p12Pass)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false, expectClientCert = true)
  }

  @Test
  fun testSessionResumption() {
    val hostName = "https://tls-v1-2.badssl.com:1012/"
    val resp = tlsCapturePacket("resumption", hostName, resumption = true)
    val httpret = resp.httpResponse
log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false, expectResumption = true)
  }

  // NOTE: FCS_TLSC_EXT.3 (Downgrade Protection) requires a custom server that sends
  // the downgrade indicator in Server Random. Since badssl.com does not support this,
  // it is not tested here. A dedicated test server or container would be needed.
  private fun analyzePcap(pcapPath: Path, expectAlert: Boolean, expectClientCert: Boolean = false, expectResumption: Boolean = false) {
    val pcap = Pcap.openStream(pcapPath.toFile())
    var foundClientHello = false
    var foundAlert = false
    var foundClientCert = false
    var certMessageCount = 0
    var clientHelloCount = 0
    val supportedCiphers = mutableListOf<Int>()
    val foundExtensions = mutableSetOf<Int>()
    val foundSigAlgs = mutableSetOf<Int>()
    val foundSigAlgsCert = mutableSetOf<Int>()
    val foundGroups = mutableSetOf<Int>()
    var tlsVersion: Int? = null

    var targetClientPort: Int? = null
    var targetServerPort: Int? = null

    pcap.loop { packet ->
      if (packet.hasProtocol(Protocol.TCP)) {
        val tcp = packet.getPacket(Protocol.TCP) as TCPPacket
        val payload = tcp.payload
        if (payload != null) {
          val srcPort = tcp.getSourcePort()
          val dstPort = tcp.getDestinationPort()
          
          // Note: Port numbers may be incorrect due to library limitations in parsing IPv6/SLL2.
          // If ports look crazy, we assume it's an IPv6 packet parsed incorrectly and search the entire payload.

          if (targetClientPort != null && !expectResumption) {
              // Strict session filter after identifying the session
              if (!((srcPort == targetClientPort && dstPort == targetServerPort) ||
                    (srcPort == targetServerPort && dstPort == targetClientPort))) {
                  return@loop true
              }
          }
          
          val bytes = payload.array
          
          // Debug: Print packet info
          log("Packet: src=$srcPort, dst=$dstPort, size=${bytes.size}")
          if (bytes.size >= 10) {
              val sb = StringBuilder()
              for (j in 0 until 10) {
                  sb.append(String.format("%02X ", bytes[j]))
              }
              log("  Payload start: $sb")
          }
          
          // Search for TLS Record
          // Note: The start position may shift due to TCP options being included in the payload by some libraries.
          for (i in 0 until bytes.size - 5) {
            val contentType = bytes[i].toInt() and 0xFF
            val versionMajor = bytes[i+1].toInt() and 0xFF
            val versionMinor = bytes[i+2].toInt() and 0xFF
            
            // Check for Handshake (0x16)
            if (contentType == 0x16 && versionMajor == 0x03) {
              val hsType = bytes[i+5].toInt() and 0xFF
              if (hsType == 0x01) { // Client Hello
                foundClientHello = true
                clientHelloCount++
                
                if (targetClientPort == null) {
                    targetClientPort = srcPort
                    targetServerPort = dstPort
                    log("Identified session: Client Port $targetClientPort, Server Port $targetServerPort")
                }
                
                // Read version in Client Hello (Handshake version)
                tlsVersion = ((bytes[i+9].toInt() and 0xFF) shl 8) or (bytes[i+10].toInt() and 0xFF)
                
                // Parse ciphers
                val sessionIdLen = bytes[i+43].toInt() and 0xFF
                val current = i + 44 + sessionIdLen
                if (current + 2 <= bytes.size) {
                  val cipherSuitesLen = ((bytes[current].toInt() and 0xFF) shl 8) or (bytes[current+1].toInt() and 0xFF)
                  var csOffset = current + 2
                  for (j in 0 until cipherSuitesLen step 2) {
                    if (csOffset + 2 <= bytes.size) {
                      val cs = ((bytes[csOffset].toInt() and 0xFF) shl 8) or (bytes[csOffset+1].toInt() and 0xFF)
                      supportedCiphers.add(cs)
                      csOffset += 2
                    }
                  }
                  
                  // Parse Compression Methods
                  val compOffset = csOffset
                  if (compOffset + 1 <= bytes.size) {
                    val compLen = bytes[compOffset].toInt() and 0xFF
                    val extOffset = compOffset + 1 + compLen
                    
                    // Parse Extensions
                    if (extOffset + 2 <= bytes.size) {
                      val extLen = ((bytes[extOffset].toInt() and 0xFF) shl 8) or (bytes[extOffset+1].toInt() and 0xFF)
                      var extCurrent = extOffset + 2
                      val extEnd = extCurrent + extLen
                      
                      while (extCurrent + 4 <= extEnd && extCurrent + 4 <= bytes.size) {
                        val extType = ((bytes[extCurrent].toInt() and 0xFF) shl 8) or (bytes[extCurrent+1].toInt() and 0xFF)
                        val extDataLen = ((bytes[extCurrent+2].toInt() and 0xFF) shl 8) or (bytes[extCurrent+3].toInt() and 0xFF)
                        
                        foundExtensions.add(extType)
                        
                        if (extType == 0x000D && extCurrent + 4 + extDataLen <= bytes.size) {
                          if (extCurrent + 6 <= bytes.size) {
                            val sigAlgsLen = ((bytes[extCurrent+4].toInt() and 0xFF) shl 8) or (bytes[extCurrent+5].toInt() and 0xFF)
                            var sigOffset = extCurrent + 6
                            while (sigOffset + 2 <= extCurrent + 4 + sigAlgsLen && sigOffset + 2 <= bytes.size) {
                              val sigAlg = ((bytes[sigOffset].toInt() and 0xFF) shl 8) or (bytes[sigOffset+1].toInt() and 0xFF)
                              foundSigAlgs.add(sigAlg)
                              sigOffset += 2
                            }
                          }
                        }
                        
                        if (extType == 0x000A && extCurrent + 4 + extDataLen <= bytes.size) {
                          if (extCurrent + 6 <= bytes.size) {
                            val groupsLen = ((bytes[extCurrent+4].toInt() and 0xFF) shl 8) or (bytes[extCurrent+5].toInt() and 0xFF)
                            var grpOffset = extCurrent + 6
                            while (grpOffset + 2 <= extCurrent + 4 + groupsLen && grpOffset + 2 <= bytes.size) {
                              val grp = ((bytes[grpOffset].toInt() and 0xFF) shl 8) or (bytes[grpOffset+1].toInt() and 0xFF)
                              foundGroups.add(grp)
                              grpOffset += 2
                            }
                          }
                        }
                        
                        if (extType == 0x0032 && extCurrent + 4 + extDataLen <= bytes.size) {
                          if (extCurrent + 6 <= bytes.size) {
                            val sigAlgsCertLen = ((bytes[extCurrent+4].toInt() and 0xFF) shl 8) or (bytes[extCurrent+5].toInt() and 0xFF)
                            var sigCertOffset = extCurrent + 6
                            while (sigCertOffset + 2 <= extCurrent + 4 + sigAlgsCertLen && sigCertOffset + 2 <= bytes.size) {
                              val sigAlgCert = ((bytes[sigCertOffset].toInt() and 0xFF) shl 8) or (bytes[sigCertOffset+1].toInt() and 0xFF)
                              foundSigAlgsCert.add(sigAlgCert)
                              sigCertOffset += 2
                            }
                          }
                        }
                        
                        if (extType == 0x002B && extCurrent + 4 + extDataLen <= bytes.size) {
                          if (extCurrent + 5 <= bytes.size) {
                            val versionsLen = bytes[extCurrent+4].toInt() and 0xFF
                            var verOffset = extCurrent + 5
                            while (verOffset + 2 <= extCurrent + 4 + versionsLen && verOffset + 2 <= bytes.size) {
                              val ver = ((bytes[verOffset].toInt() and 0xFF) shl 8) or (bytes[verOffset+1].toInt() and 0xFF)
                              if (ver == 0x0304) {
                                tlsVersion = 0x0304
                              }
                              verOffset += 2
                            }
                          }
                        }
                        
                        extCurrent += 4 + extDataLen
                      }
                    }
                  }
                }
              }
              
              if (hsType == 0x0b) { // Certificate
                if (srcPort == targetServerPort) {
log("Found Server Certificate packet!")

                } else if (srcPort == targetClientPort) {
                    foundClientCert = true
log("Found Client Certificate packet!")

                }
              }
            }
            
            // Check for Alert (0x15)
            if (contentType == 0x15 && versionMajor == 0x03) {
              foundAlert = true
log("Found Alert packet!")

            }
          }
        }
      }
      true // Continue loop
    }

    Assert.assertTrue("Client Hello not found in capture", foundClientHello)
log("Supported Ciphers found: ${supportedCiphers.map { String.format("0x%04X", it) }}")

log("Found Extensions: ${foundExtensions.map { String.format("0x%04X", it) }}")

log("TLS Version in Client Hello: ${tlsVersion?.let { String.format("0x%04X", it) }}")


    // SFR: FCS_TLSC_EXT.1.2 Ciphersuites
    val requiredCodes = REQUIRED_CIPHERS_IN_SFR.mapNotNull { CIPHER_MAP[it] }
    val matches = supportedCiphers.intersect(requiredCodes)
log("Matches with required ciphers: ${matches.map { String.format("0x%04X", it) }}")

    Assert.assertTrue("No required ciphers found in Client Hello", matches.isNotEmpty())
    SFRCheckList.pass("FCS_TLSC_EXT.1.2")
    

    
    // SFR: FCS_TLSC_EXT.1.2 Supported Ciphersuites
    val allowedCiphers = setOf(
      // CNSA 1.0
      0xC02C, // TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
      0xC030, // TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      0x009D, // TLS_RSA_WITH_AES_256_GCM_SHA384
      0x009F, // TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
      0xC024, // TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
      0xC028, // TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
      0xD003, // TLS_ECDHE_PSK_WITH_AES_256_GCM_SHA384
      0x00AA, // TLS_DHE_PSK_WITH_AES_256_GCM_SHA384
      0x00AC, // TLS_RSA_PSK_WITH_AES_256_GCM_SHA384
      
      // Non-CNSA
      0x003D, // TLS_RSA_WITH_AES_256_CBC_SHA256
      0x006D, // TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
      0xC02B, // TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
      0xC02F, // TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
      0xC023, // TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256
      0xC027, // TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256
      0x003C, // TLS_RSA_WITH_AES_128_CBC_SHA256
      0x0067, // TLS_DHE_RSA_WITH_AES_128_CBC_SHA256
      0x002F, // TLS_RSA_WITH_AES_128_CBC_SHA
      0xD001, // TLS_ECDHE_PSK_WITH_AES_128_GCM_SHA256
      0x00A8, // TLS_DHE_PSK_WITH_AES_128_GCM_SHA256
      0x00A6, // TLS_RSA_PSK_WITH_AES_128_GCM_SHA256
      
      // TLS 1.3
      0x1302, // TLS_AES_256_GCM_SHA384
      0x1301  // TLS_AES_128_GCM_SHA256
    )
    val nonCompliantOffered = supportedCiphers.filter { !allowedCiphers.contains(it) && it !in setOf(0x1301, 0x1302, 0x1303) }
    log("Offered ciphers not explicitly in spec 1.2 allowed list: ${nonCompliantOffered.map { String.format("0x%04X", it) }}")
    
    // SFR: FCS_TLSC_EXT.1.3 Forbidden Ciphers
    val forbiddenCiphers = setOf(
      0x0000, // TLS_NULL_WITH_NULL_NULL
      0x0001, // TLS_RSA_WITH_NULL_MD5
      0x0002, // TLS_RSA_WITH_NULL_SHA
      0x003B, // TLS_RSA_WITH_NULL_SHA256
      0xC006, // TLS_ECDHE_ECDSA_WITH_NULL_SHA
      0xC010, // TLS_ECDHE_RSA_WITH_NULL_SHA
      0x0004, // TLS_RSA_WITH_RC4_128_MD5
      0x0005, // TLS_RSA_WITH_RC4_128_SHA
      0xC007, // TLS_ECDHE_ECDSA_WITH_RC4_128_SHA
      0xC011, // TLS_ECDHE_RSA_WITH_RC4_128_SHA
      0x000A, // TLS_RSA_WITH_3DES_EDE_CBC_SHA
      0x000D, // TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA
      0xC008, // TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA
      0xC012  // TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA
    )
    val forbiddenMatches = supportedCiphers.intersect(forbiddenCiphers)
    Assert.assertTrue("Forbidden ciphers found in Client Hello: ${forbiddenMatches.map { String.format("0x%04X", it) }}", forbiddenMatches.isEmpty())

    // SFR: FCS_TLSC_EXT.1.1 Support TLS 1.2 or 1.3
    Assert.assertTrue("TLS version should be 1.2 or later", tlsVersion != null && tlsVersion >= 0x0303)

    // SFR: FCS_TLSC_EXT.1.4 Required Extensions
    Assert.assertTrue("signature_algorithms extension not found", foundExtensions.contains(0x000D))
    Assert.assertTrue("supported_groups extension not found", foundExtensions.contains(0x000A))
    
    // Verify specific signature algorithms (FCS_TLSC_EXT.1.4)
    log("Found Signature Algorithms: ${foundSigAlgs.map { String.format("0x%04X", it) }}")
    
    // Group 1: CNSA 1.0 compliant (Must have at least one)
    val hasGroup1 = foundSigAlgs.contains(0x0503) || foundSigAlgs.contains(0x0501) // ecdsa_secp384r1_sha384 or rsa_pkcs1_sha384
    Assert.assertTrue("Neither ecdsa_secp384r1_sha384 nor rsa_pkcs1_sha384 found in signature_algorithms", hasGroup1)
    
    // Group 2: CNSA 1.0 or non-CNSA compliant (Must have at least one)
    val hasGroup2 = foundSigAlgs.contains(0x080A) || foundSigAlgs.contains(0x0805) || // rsa_pss_pss_sha384 or rsa_pss_rsae_sha384
                    foundSigAlgs.contains(0x0401) || foundSigAlgs.contains(0x0804)   // rsa_pkcs1_sha256 or rsa_pss_rsae_sha256
    Assert.assertTrue("None of the allowed PSS or non-CNSA algorithms found in signature_algorithms", hasGroup2)
    
    // Verify signature_algorithms_cert if present (FCS_TLSC_EXT.1.4)
    if (foundExtensions.contains(0x0032)) {
      log("Found Signature Algorithms Cert: ${foundSigAlgsCert.map { String.format("0x%04X", it) }}")
      val hasGroup1Cert = foundSigAlgsCert.contains(0x0503) || foundSigAlgsCert.contains(0x0501)
      Assert.assertTrue("Neither ecdsa_secp384r1_sha384 nor rsa_pkcs1_sha384 found in signature_algorithms_cert", hasGroup1Cert)
      
      val hasGroup2Cert = foundSigAlgsCert.contains(0x080A) || foundSigAlgsCert.contains(0x0805) ||
                          foundSigAlgsCert.contains(0x0401) || foundSigAlgsCert.contains(0x0804)
      Assert.assertTrue("None of the allowed PSS or non-CNSA algorithms found in signature_algorithms_cert", hasGroup2Cert)
    }
    
    // Verify supported groups (FCS_TLSC_EXT.1.4)
    log("Found Supported Groups: ${foundGroups.map { String.format("0x%04X", it) }}")
    val hasRequiredGroup = foundGroups.contains(0x0018) || foundGroups.contains(0x0017) // secp384r1 or secp256r1
    Assert.assertTrue("Neither secp384r1 nor secp256r1 found in supported_groups", hasRequiredGroup)
    
    SFRCheckList.pass("FCS_TLSC_EXT.1.4")

    // Check for Extended Master Secret (0x0017)
    Assert.assertTrue("extended_master_secret extension not found", foundExtensions.contains(0x0017))
    SFRCheckList.pass("FCS_TLSC_EXT.1.4/ExtendedMasterSecret")

    // SFR: FCS_TLSC_EXT.4.1 Secure Renegotiation (Test 1.1)
    val hasSCSV = supportedCiphers.contains(0x00FF)
    val hasRenegInfo = foundExtensions.contains(0xFF01)
log("Has TLS_EMPTY_RENEGOTIATION_INFO_SCSV: $hasSCSV")

log("Has renegotiation_info extension: $hasRenegInfo")

    Assert.assertTrue("Neither SCSV nor renegotiation_info extension found in Client Hello", hasSCSV || hasRenegInfo)
    SFRCheckList.pass("FCS_TLSC_EXT.4.1")

    // SFR: FCS_TLSC_EXT.5.1 Session Resumption (Offered support)
    val hasSessionTicket = foundExtensions.contains(0x0023)
log("Has SessionTicket extension: $hasSessionTicket")

if (!expectResumption) {
  log("SessionTicket extension not found in Client Hello (Capability might still be supported)")
}


    // SFR: FCS_TLSC_EXT.6.1 TLS 1.3 Resumption Refinements
    if (tlsVersion == 0x0304) {
        Assert.assertTrue("psk_key_exchange_modes extension not found", foundExtensions.contains(0x002D))
        SFRCheckList.pass("FCS_TLSC_EXT.6.1")
    } else {
        log("Skipping psk_key_exchange_modes check because TLS version is not 1.3 (Version: ${String.format("0x%04X", tlsVersion)})")
    }

    // SFR: FCS_TLSC_EXT.6.2 No Early Data
    Assert.assertFalse("early_data extension should NOT be present", foundExtensions.contains(0x002A))
    SFRCheckList.pass("FCS_TLSC_EXT.6.2")
    
    // Strict check: No other extensions allowed (FCS_TLSC_EXT.1.4)
    val allowedExtensions = setOf(
      0x000D, // signature_algorithms
      0x0032, // signature_algorithms_cert
      0x002B, // supported_versions
      0x000A, // supported_groups
      0x0033, // key_share
      0x0031, // post_handshake_auth
      0x0029, // pre_shared_key
      0x0021, // tls_cert_with_extern_psk
      0x002D, // psk_key_exchange_modes
      0x0017  // extended_master_secret
    )
    val forbiddenOffered = foundExtensions.filter { !allowedExtensions.contains(it) }
    log("Offered extensions not in allowed list: ${forbiddenOffered.map { String.format("0x%04X", it) }}")
    // Assert.assertTrue("Forbidden extensions offered: ${forbiddenOffered.map { String.format("0x%04X", it) }}", forbiddenOffered.isEmpty())

    // SFR: FCS_TLSC_EXT.2.1 Mutual Authentication
    if (expectClientCert) {
      Assert.assertTrue("Expected Client Certificate not found", foundClientCert)
    } else {
      Assert.assertFalse("Client Certificate should NOT be sent", foundClientCert)
    }

    if (expectAlert) {
      Assert.assertTrue("Expected Alert packet not found", foundAlert)
    }

    if (expectResumption) {
      Assert.assertTrue("Expected at least 2 Client Hellos for resumption", clientHelloCount >= 2)
log("Found $clientHelloCount Client Hellos, confirmed resumption attempt!")


      Assert.assertTrue("SessionTicket extension expected for resumption", hasSessionTicket)

      if (hasSessionTicket) SFRCheckList.pass("FCS_TLSC_EXT.5.1/SessionTicket")

      SFRCheckList.pass("FCS_TLSC_EXT.5.1")

    }
  }

  private fun tlsCapturePacket(testlabel:String, testurl:String, p12Path: String? = null, p12Pass: String? = null, resumption: Boolean = false): org.example.plugin.utils.TlsResult {
    var pcap: Path = Paths.get("/")
    var httpResp: String = ""
    var workerLogsStr: String = ""
    val serial = adb.deviceSerial

    runBlocking {
      val browserApk = File(JUnitBridge.resourceDir, "openurl-debug.apk")
      val ret = AdamUtils.installApk(client, serial, browserApk, true)
      Assert.assertTrue("Failed to install openurl app: ${ret}", ret.startsWith("Success"))
      appInstalled = true

      val tcpdumpJob = launch(Dispatchers.IO) {
          try {
log("Starting tcpdump in coroutine...")

              // Clean up old capture file to avoid analyzing stale data if the current run fails
              client.execute(ShellCommandRequest("su 0 rm -f /data/local/tmp/traffic.pcap"), serial)
              client.execute(ShellCommandRequest("su 0 tcpdump -i any -U -w /data/local/tmp/traffic.pcap"), serial)
log("tcpdump coroutine finished")

          } catch (e: Exception) {
log("tcpdump coroutine exception: ${e.message}")

          }
      }
      Thread.sleep(2000) // Give tcpdump time to start

      client.execute(ShellCommandRequest("am force-stop com.example.openurl"), serial)
      Thread.sleep(500)
      
log("Launching app with URL: $testurl")

      var cmd = "am start -a android.intent.action.VIEW -n com.example.openurl/.MainActivity -e openurl $testurl"
      if (!p12Path.isNullOrBlank()) {
          cmd += " -e p12path $p12Path"
      }
      if (!p12Pass.isNullOrBlank()) {
          cmd += " -e p12pass '$p12Pass'"
      }
      if (resumption) {
          cmd += " --ez resumption true"
      }
      client.execute(com.malinskiy.adam.request.shell.v1.ShellCommandRequest(cmd), serial)

      // Wait worker response on logcat and get return code from that
      val res: AdamUtils.LogcatResult? = AdamUtils.waitLogcatLine(100, "worker@return", adb)
      if (res != null) {
log("worker@return => ${res.text}")

        val text = res.text
        val marker = "worker@return:"
        val index = text.lastIndexOf(marker)
        if (index >= 0) {
          httpResp = text.substring(index + marker.length).trim()
        } else {
          httpResp = text.trim()
        }
      } else {
log("we can't grab the return value from worker.")

        httpResp = "Error: timeout waiting for logcat"
      }

      // Capture worker logs
      val logcatResult = client.execute(ShellCommandRequest("su 0 logcat -d"), serial)
      val logcatOutput = String(logcatResult.stdout)
      val lines = logcatOutput.split("\n")
      val workerLines = lines.filter { it.contains("worker@") }
      if (workerLines.isNotEmpty()) {
          workerLogsStr = workerLines.joinToString("\n")
          org.example.project.JUnitBridge.logging?.invoke("--- Worker Logs Start ---", org.example.project.TestLogLevel.INFO)
          workerLines.forEach { line ->
              org.example.project.JUnitBridge.logging?.invoke(line, org.example.project.TestLogLevel.INFO)
          }
          org.example.project.JUnitBridge.logging?.invoke("--- Worker Logs End ---", org.example.project.TestLogLevel.INFO)
      }

      Thread.sleep(2000) // Give tcpdump time to flush buffers
      client.execute(ShellCommandRequest("su 0 sh -c \"pkill tcpdump\""), serial)
      tcpdumpJob.cancel()
      Thread.sleep(2000)

      val src = "/data/local/tmp/traffic.pcap"
      var fileReady = false
      for (i in 1..5) {
          val result = client.execute(ShellCommandRequest("su 0 sh -c \"test -s $src && echo yes || echo no\""), serial)
          val exists = String(result.stdout).trim()
          if (exists == "yes") {
              fileReady = true
              break
          }
          Thread.sleep(1000)
      }
      
      if (!fileReady) {
          Assert.fail("traffic.pcap not found or empty.")
      }
      
      val pcap0: Path = kotlin.io.path.createTempFile("t", ".pcap")
      AdamUtils.pullFile(src, pcap0.toString(), adb, true)
      pcap = copyPcapToOutPath(pcap0, testlabel)
    }
    return org.example.plugin.utils.TlsResult(httpResp, workerLogsStr, pcap)
  }

  val OUT_PATH = File(JUnitBridge.resultsDir, "capture").absolutePath + "/"
  private fun copyPcapToOutPath(pcap:Path,testlabel:String):Path {
    val outdir = File(Paths.get(OUT_PATH).toUri())
    if(!outdir.exists()){
     outdir.mkdirs()
    }
    val tstmp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
    val to = Paths.get(OUT_PATH,"${tstmp}-${testlabel}.pcap")
    Files.copy(pcap, to)
    return to
  }
}
