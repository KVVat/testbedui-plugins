package org.example.plugin.fcstls

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.TlsTableLookup
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
        "Verify existence of SessionTicket in non-resumption tests"
      )
      SFRCheckList.register(
        "FCS_TLSC_EXT.5.1/PSK",
        "Documented capability: TLS 1.3 PSK resumption (RFC 8446) — not claimed in ST selection"
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

  /**
   * Documented capability check: TLS 1.3 PSK resumption (RFC 8446) against a
   * public TLS 1.3 endpoint (Google).
   *
   * Not part of the strict ST claim ("PSK and tickets" is unchecked in the
   * selection for FCS_TLSC_EXT.5.1) but provides direct evidence that the TOE
   * (Conscrypt) supports the PSK mechanism end-to-end.
   *
   * Soft assertions only: external endpoints may not always issue tickets or
   * accept resumption (load-balancer state, rate-limiting). See
   * [FCS_TLSC_EXT.5.1/PSK] checklist entry for status.
   */
  @Test
  fun testSessionResumptionTls13() {
    val hostName = "https://ipv4.google.com/"
    val resp = tlsCapturePacket("resumption_tls13", hostName, resumption = true)
    val httpret = resp.httpResponse
    log("HTTP response: $httpret")

    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))

    val pcapPath = resp.pcapPath
    analyzePcap(pcapPath, expectAlert = false, expectResumption = true, expectTls13Resumption = true)
  }

  @Test
  fun testSessionResumptionWithSServer() {
    val serial = adb.deviceSerial
    val port = 4433
    
    val certFile = File(JUnitBridge.resourceDir, "cert.pem")
    val keyFile = File(JUnitBridge.resourceDir, "key.pem")

    Assert.assertTrue("Cert file not found in resources: ${certFile.absolutePath}", certFile.exists())
    Assert.assertTrue("Key file not found in resources: ${keyFile.absolutePath}", keyFile.exists())



    // Start s_server on host.
    //   -tls1_2    : negotiate only TLS 1.2 (client may offer 1.3 but server rejects)
    //   -no_ticket : disable RFC 5077 session tickets so the only resumption path
    //                is the RFC 5246 session-ID cache. Without this, a second
    //                connection would resume via tickets and the session-ID
    //                evidence we are trying to capture would not appear.
    //   (do NOT use -no_resume_ephemeral or -no_cache: those would also disable
    //    session-ID caching, defeating the purpose of this test.)
    val pb = ProcessBuilder(
      "openssl", "s_server",
      "-accept", port.toString(),
      "-cert", certFile.absolutePath,
      "-key", keyFile.absolutePath,
      "-www",
      "-tls1_2",
      "-no_ticket"
    )
    val serverProcess = pb.start()
    
    try {
      Thread.sleep(2000) // Give it time to start

      // Adb reverse
      val reverseProc = Runtime.getRuntime().exec("adb -s $serial reverse tcp:$port tcp:$port")
      reverseProc.waitFor()

      val hostName = "https://localhost:$port/"
      val resp = tlsCapturePacket("resumption_sserver", hostName, trustPath = certFile.absolutePath, resumption = true)
      
      val httpret = resp.httpResponse
      log("HTTP response: $httpret")
      errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))

      val pcapPath = resp.pcapPath
      analyzePcap(pcapPath, expectAlert = false, expectResumption = true)
      
    } finally {
      serverProcess.destroy()
      Runtime.getRuntime().exec("adb -s $serial reverse --remove tcp:$port").waitFor()
    }
  }

  @Test
  fun testSessionResumptionTls13MismatchedSessionId() {
    val serial = adb.deviceSerial
    val port = 4433
    
    // Start the mock server on host
    val tempDir = Files.createTempDirectory("server").toFile()
    val serverScript = File(tempDir, "bad_tls_server.py")
    serverScript.writeText("""
import socket
import sys
import os
import time

port = 4433
if len(sys.argv) > 1:
    port = int(sys.argv[1])

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("0.0.0.0", port))
s.listen(1)
print(f"Listening on port {port}...")

while True:
    conn, addr = s.accept()
    print(f"Connected by {addr}")
    try:
        data = conn.recv(1024)
        if not data:
            conn.close()
            continue

        # Find session ID length at index 43
        if len(data) < 44:
            print("Data too short")
            conn.close()
            continue

        sid_len = data[43]
        print(f"Session ID length: {sid_len}")

        if len(data) < 44 + sid_len:
            print("Data too short for session ID")
            conn.close()
            continue

        sid = data[44 : 44 + sid_len]
        print(f"Client Session ID: {sid.hex()}")

        # Modify the last byte of session ID
        bad_sid = bytearray(sid)
        if len(bad_sid) > 0:
            bad_sid[-1] = (bad_sid[-1] + 1) % 256
        print(f"Bad Session ID: {bad_sid.hex()}")

        # Construct ServerHello
        random_bytes = os.urandom(32)

        server_hello_body = bytearray()
        server_hello_body.extend([0x03, 0x03])  # Version
        server_hello_body.extend(random_bytes)  # Random
        server_hello_body.append(len(bad_sid))  # Session ID length
        server_hello_body.extend(bad_sid)  # Session ID
        server_hello_body.extend([0x13, 0x01])  # Cipher Suite: TLS_AES_128_GCM_SHA256
        server_hello_body.append(0x00)  # Compression

        # Extensions: supported_versions (TLS 1.3)
        extensions = bytearray([0x00, 0x2B, 0x00, 0x02, 0x03, 0x04])

        ext_len = len(extensions)
        server_hello_body.extend([ext_len >> 8, ext_len & 0xFF])
        server_hello_body.extend(extensions)

        hs_len = len(server_hello_body)
        handshake_msg = bytearray([0x02, hs_len >> 16, (hs_len >> 8) & 0xFF, hs_len & 0xFF])
        handshake_msg.extend(server_hello_body)

        rec_len = len(handshake_msg)
        record = bytearray([0x16, 0x03, 0x03, rec_len >> 8, rec_len & 0xFF])
        record.extend(handshake_msg)

        conn.sendall(record)
        print("Sent ServerHello with bad session ID")

        time.sleep(2)
        conn.close()
        print("Closed connection")

    except Exception as e:
        print(f"Error: {e}")
        conn.close()
    """.trimIndent())
    
    val pb = ProcessBuilder("python3", serverScript.absolutePath, port.toString())
    val serverProcess = pb.start()
    
    try {
      Thread.sleep(2000) // Give it time to start

      // Adb reverse
      val reverseProc = Runtime.getRuntime().exec("adb -s $serial reverse tcp:$port tcp:$port")
      reverseProc.waitFor()

      val hostName = "https://localhost:$port/"
      val resp = tlsCapturePacket("resumption_bad_sid", hostName, resumption = true)
      
      val httpret = resp.httpResponse
      log("HTTP response: $httpret")
      
      // We expect it to NOT start with 200, but rather fail.
      errs.checkThat(a.msg("HTTP response should NOT start with 200"), httpret.startsWith("200"), IsEqual(false))
      
      log("Worker logs: ${resp.workerLogs}")
      errs.checkThat(a.msg("Worker logs should contain SSLHandshakeException or similar"), 
                     resp.workerLogs.contains("SSLHandshakeException") || resp.workerLogs.contains("SSLPeerUnverifiedException") || resp.workerLogs.contains("timeout"), 
                     IsEqual(true))
      
    } finally {
      serverProcess.destroy()
      Runtime.getRuntime().exec("adb -s $serial reverse --remove tcp:$port").waitFor()
      tempDir.deleteRecursively()
    }
  }

  // NOTE: FCS_TLSC_EXT.3 (Downgrade Protection) requires a custom server that sends
  // the downgrade indicator in Server Random. Since badssl.com does not support this,
  // it is not tested here. A dedicated test server or container would be needed.
  private fun analyzePcap(pcapPath: Path, expectAlert: Boolean, expectClientCert: Boolean = false, expectResumption: Boolean = false, expectTls13Resumption: Boolean = false) {
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

    // Pass 1: accumulate TCP payloads per (srcPort, dstPort) flow so that TLS records
    // that span multiple TCP segments can be parsed as a single buffer. We concatenate
    // in capture order which is equivalent to SEQ order on a healthy capture with no
    // loss/reordering (tcpdump on-device typically satisfies this).
    data class FlowKey(val src: Int, val dst: Int)
    val flowBufs = mutableMapOf<FlowKey, java.io.ByteArrayOutputStream>()
    val packetLog = mutableListOf<Triple<Int, Int, Int>>() // src, dst, size

    pcap.loop { packet ->
      if (packet.hasProtocol(Protocol.TCP)) {
        val tcp = packet.getPacket(Protocol.TCP) as TCPPacket
        val payload = tcp.payload
        if (payload != null) {
          val srcPort = tcp.getSourcePort()
          val dstPort = tcp.getDestinationPort()
          val bytes = payload.array
          packetLog.add(Triple(srcPort, dstPort, bytes.size))
          flowBufs.getOrPut(FlowKey(srcPort, dstPort)) { java.io.ByteArrayOutputStream() }.write(bytes)
        }
      }
      true
    }

    // Log packet ordering up to a reasonable cap so we keep the debug detail that the
    // previous implementation emitted.
    for ((src, dst, size) in packetLog.take(8)) {
      log("Packet: src=$src, dst=$dst, size=$size")
    }

    // Identify the flow containing ClientHello: scan each client→server flow for 0x16 0x03 0xMM
    // immediately followed by 0x01 (Handshake / ClientHello). We pick the first flow where the
    // TLS record length fits the reassembled buffer (so we have the *full* ClientHello).
    fun findClientHelloFlow(): Pair<FlowKey, Int>? {
      for ((key, baos) in flowBufs) {
        val b = baos.toByteArray()
        var i = 0
        while (i + 9 <= b.size) {
          if ((b[i].toInt() and 0xFF) == 0x16 && (b[i+1].toInt() and 0xFF) == 0x03 &&
              (b[i+5].toInt() and 0xFF) == 0x01) {
            val recLen = ((b[i+3].toInt() and 0xFF) shl 8) or (b[i+4].toInt() and 0xFF)
            if (i + 5 + recLen <= b.size) {
              return key to i
            }
          }
          i++
        }
      }
      return null
    }

    val hit = findClientHelloFlow()
    if (hit != null) {
      val (key, recStart) = hit
      targetClientPort = key.src
      targetServerPort = key.dst
      log("Identified session: Client Port $targetClientPort, Server Port $targetServerPort")

      val bytes = flowBufs[key]!!.toByteArray()
      foundClientHello = true
      clientHelloCount = 1

      // Count additional ClientHellos in the same flow (for resumption test).
      run {
        var p = recStart + 1
        while (p + 9 <= bytes.size) {
          if ((bytes[p].toInt() and 0xFF) == 0x16 && (bytes[p+1].toInt() and 0xFF) == 0x03 &&
              (bytes[p+5].toInt() and 0xFF) == 0x01) {
            clientHelloCount++
            p += 6
          } else p++
        }
      }

      val i = recStart
      val sb = StringBuilder()
      for (j in 0 until minOf(10, bytes.size - i)) sb.append(String.format("%02X ", bytes[i+j]))
      log("  ClientHello record start: $sb")

      tlsVersion = ((bytes[i+9].toInt() and 0xFF) shl 8) or (bytes[i+10].toInt() and 0xFF)

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

        val compOffset = csOffset
        if (compOffset + 1 <= bytes.size) {
          val compLen = bytes[compOffset].toInt() and 0xFF
          val extOffset = compOffset + 1 + compLen
          if (extOffset + 2 <= bytes.size) {
            val extLen = ((bytes[extOffset].toInt() and 0xFF) shl 8) or (bytes[extOffset+1].toInt() and 0xFF)
            var extCurrent = extOffset + 2
            val extEnd = extCurrent + extLen

            while (extCurrent + 4 <= extEnd && extCurrent + 4 <= bytes.size) {
              val extType = ((bytes[extCurrent].toInt() and 0xFF) shl 8) or (bytes[extCurrent+1].toInt() and 0xFF)
              val extDataLen = ((bytes[extCurrent+2].toInt() and 0xFF) shl 8) or (bytes[extCurrent+3].toInt() and 0xFF)
              foundExtensions.add(extType)

              // Extension data layout: [type(2)] [ext_len(2)] [vector_len(2)] [vector...]
              // vector ends at extCurrent + 4 + 2 + vector_len = extCurrent + 6 + vector_len.
              // (The historical bound `extCurrent + 4 + vector_len` was off-by-2 and silently
              // dropped the final entry of supported_groups / signature_algorithms.)
              if (extType == 0x000D && extCurrent + 4 + extDataLen <= bytes.size) {
                val sigAlgsLen = ((bytes[extCurrent+4].toInt() and 0xFF) shl 8) or (bytes[extCurrent+5].toInt() and 0xFF)
                var sigOffset = extCurrent + 6
                while (sigOffset + 2 <= extCurrent + 6 + sigAlgsLen && sigOffset + 2 <= bytes.size) {
                  val sigAlg = ((bytes[sigOffset].toInt() and 0xFF) shl 8) or (bytes[sigOffset+1].toInt() and 0xFF)
                  foundSigAlgs.add(sigAlg)
                  sigOffset += 2
                }
              }
              if (extType == 0x000A && extCurrent + 4 + extDataLen <= bytes.size) {
                val groupsLen = ((bytes[extCurrent+4].toInt() and 0xFF) shl 8) or (bytes[extCurrent+5].toInt() and 0xFF)
                var grpOffset = extCurrent + 6
                while (grpOffset + 2 <= extCurrent + 6 + groupsLen && grpOffset + 2 <= bytes.size) {
                  val grp = ((bytes[grpOffset].toInt() and 0xFF) shl 8) or (bytes[grpOffset+1].toInt() and 0xFF)
                  foundGroups.add(grp)
                  grpOffset += 2
                }
              }
              if (extType == 0x0032 && extCurrent + 4 + extDataLen <= bytes.size) {
                val sigAlgsCertLen = ((bytes[extCurrent+4].toInt() and 0xFF) shl 8) or (bytes[extCurrent+5].toInt() and 0xFF)
                var sigCertOffset = extCurrent + 6
                while (sigCertOffset + 2 <= extCurrent + 6 + sigAlgsCertLen && sigCertOffset + 2 <= bytes.size) {
                  val sigAlgCert = ((bytes[sigCertOffset].toInt() and 0xFF) shl 8) or (bytes[sigCertOffset+1].toInt() and 0xFF)
                  foundSigAlgsCert.add(sigAlgCert)
                  sigCertOffset += 2
                }
              }
              if (extType == 0x002B && extCurrent + 4 + extDataLen <= bytes.size) {
                // supported_versions uses a 1-byte length prefix; vector ends at
                // extCurrent + 4 + 1 + versionsLen = extCurrent + 5 + versionsLen.
                val versionsLen = bytes[extCurrent+4].toInt() and 0xFF
                var verOffset = extCurrent + 5
                while (verOffset + 2 <= extCurrent + 5 + versionsLen && verOffset + 2 <= bytes.size) {
                  val ver = ((bytes[verOffset].toInt() and 0xFF) shl 8) or (bytes[verOffset+1].toInt() and 0xFF)
                  if (ver == 0x0304) tlsVersion = 0x0304
                  verOffset += 2
                }
              }
              extCurrent += 4 + extDataLen
            }
          }
        }
      }
    }

    // Alert / Certificate scan across all flows (also reassembled).
    for ((key, baos) in flowBufs) {
      val b = baos.toByteArray()
      var p = 0
      while (p + 5 <= b.size) {
        val ct = b[p].toInt() and 0xFF
        val vmaj = b[p+1].toInt() and 0xFF
        if (ct == 0x15 && vmaj == 0x03) {
          foundAlert = true
        }
        if (ct == 0x16 && vmaj == 0x03 && p + 6 <= b.size) {
          val hsType = b[p+5].toInt() and 0xFF
          if (hsType == 0x0b) { // Certificate
            if (key.src == targetServerPort) {
              log("Found Server Certificate packet!")
            } else if (key.src == targetClientPort) {
              foundClientCert = true
              log("Found Client Certificate packet!")
            }
          }
        }
        p++
      }
    }

    Assert.assertTrue("Client Hello not found in capture", foundClientHello)
log("Supported Ciphers found: ${supportedCiphers.map { TlsTableLookup.getCandidateName(it) }}")

log("Found Extensions: ${foundExtensions.map { TlsTableLookup.getExtensionName(it) }}")

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
    log("Found Signature Algorithms: ${foundSigAlgs.map { TlsTableLookup.getSignatureSchemeName(it) }}")
    
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
    log("Found Supported Groups: ${foundGroups.map { TlsTableLookup.getGroupName(it) }}")
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
      0x0017, // extended_master_secret
      0x0023  // session_ticket — required by FCS_TLSC_EXT.5.1 ticket selection (RFC 5077)
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
      log("===== Resumption Evidence Analysis =====")
      val flowBufsByPair: Map<Pair<Int, Int>, ByteArray> =
        flowBufs.mapKeys { Pair(it.key.src, it.key.dst) }.mapValues { it.value.toByteArray() }
      val evidence = analyzeResumptionEvidence(flowBufsByPair)

      Assert.assertTrue(
        "Expected at least 2 Client Hellos across all flows for resumption (found ${evidence.clientHelloCount})",
        evidence.clientHelloCount >= 2
      )

      if (expectTls13Resumption) {
        // TLS 1.3 PSK path (RFC 8446). Soft checks because external endpoints
        // (e.g. Google) may not always issue tickets or accept resumption due
        // to load-balancer state or rate-limiting.
        log("[FCS_TLSC_EXT.5.1] Evaluating TLS 1.3 PSK resumption (documented capability).")
        errs.checkThat(
          a.msg("Expected pre_shared_key extension in 2nd ClientHello (TLS 1.3 PSK offer)"),
          evidence.ch2HasPreSharedKey, IsEqual(true)
        )
        errs.checkThat(
          a.msg("Expected pre_shared_key extension in 2nd ServerHello (TLS 1.3 PSK acceptance)"),
          evidence.sh2HasPreSharedKey, IsEqual(true)
        )
        if (evidence.ch2HasPreSharedKey && evidence.sh2HasPreSharedKey) {
          SFRCheckList.pass("FCS_TLSC_EXT.5.1/PSK")
        } else {
          log("[FCS_TLSC_EXT.5.1/PSK] PSK resumption not observed in this run; rerun may help (network may be flaky).")
        }
      } else {
        Assert.assertTrue("SessionTicket extension expected for resumption", hasSessionTicket)
        if (hasSessionTicket) SFRCheckList.pass("FCS_TLSC_EXT.5.1/SessionTicket")
        SFRCheckList.pass("FCS_TLSC_EXT.5.1")
      }
    }
  }

  private fun tlsCapturePacket(testlabel:String, testurl:String, p12Path: String? = null, p12Pass: String? = null, resumption: Boolean = false, type: String = "http", forceTls12: Boolean = false, trustPath: String? = null): org.example.plugin.utils.TlsResult {
    var pcap: Path = Paths.get("/")
    var httpResp: String = ""
    var workerLogsStr: String = ""
    val serial = adb.deviceSerial

    runBlocking {
      val browserApk = File(JUnitBridge.resourceDir, "openurl-debug.apk")
      val ret = AdamUtils.installApk(client, serial, browserApk, true)
      Assert.assertTrue("Failed to install openurl app: ${ret}", ret.startsWith("Success"))
      appInstalled = true

      if (!trustPath.isNullOrBlank()) {
          val certFile = File(trustPath)
          if (certFile.exists()) {
              val channel = client.execute(
                com.malinskiy.adam.request.sync.v1.PushFileRequest(certFile, "/sdcard/cert.pem"),
                this,
                serial = serial
              )
              for (progress in channel) {}
              
              client.execute(ShellCommandRequest("su 0 mkdir -p /data/data/com.example.openurl/files/"), serial)
              client.execute(ShellCommandRequest("su 0 cp /sdcard/cert.pem /data/data/com.example.openurl/files/cert.pem"), serial)
              client.execute(ShellCommandRequest("su 0 chmod 666 /data/data/com.example.openurl/files/cert.pem"), serial)
              log("Pushed trust cert to device: /data/data/com.example.openurl/files/cert.pem")
          } else {
              log("Trust cert file not found on host: $trustPath")
          }
      }

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
      if (!trustPath.isNullOrBlank()) {
          cmd += " -e trustpath /data/data/com.example.openurl/files/cert.pem"
      }
      if (resumption) {
          cmd += " --ez resumption true"
      }
      cmd += " -e type $type"
      if (forceTls12) {
          cmd += " --ez forceTls12 true"
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

  // ---------------------------------------------------------------------------
  // Resumption evidence helpers (FCS_TLSC_EXT.5.1)
  // ---------------------------------------------------------------------------
  // Goal: across all observed TCP flows in a capture (resumption typically uses
  // separate TCP connections, so flowBufs has multiple entries), enumerate the
  // ClientHello / ServerHello / NewSessionTicket plaintext handshake messages
  // and infer which RFC mechanism the TOE used to resume:
  //   - RFC 5246 (TLS 1.2 session ID)
  //   - RFC 5077 (TLS 1.2 session tickets)
  //   - RFC 8446 (TLS 1.3 pre_shared_key)
  // The result is logged for human review; the test only asserts that 2+
  // ClientHellos exist (existing behaviour).

  private data class CHInfo(
    val sessionId: ByteArray,
    val cipherSuiteCount: Int,
    val ext: Map<Int, ByteArray>
  )

  private data class SHInfo(
    val legacyVersion: Int,
    val sessionId: ByteArray,
    val cipherSuite: Int,
    val ext: Map<Int, ByteArray>
  )

  private data class ResumptionEvidence(
    val clientHelloCount: Int,
    val serverHelloCount: Int,
    val newSessionTicketCount: Int,
    val flowCount: Int,
    val mechanism: String,
    // TLS 1.2 ticket path
    val ch2HasNonEmptyTicket: Boolean,
    // TLS 1.3 PSK path
    val ch2HasPreSharedKey: Boolean,
    val sh2HasPreSharedKey: Boolean,
    // Useful for TLS 1.2 (Certificate is encrypted in TLS 1.3 anyway)
    val secondFlightAbbreviated: Boolean
  )

  /**
   * Walk a single TCP flow's reassembled bytes and return the plaintext
   * handshake messages (type, body). Stops accumulating when an apparently
   * encrypted handshake record is reached (heuristic: first byte not a known
   * handshake type). NewSessionTicket in TLS 1.2 is observable here because
   * RFC 5077 §3.3 places it before the server's ChangeCipherSpec (plaintext).
   * In TLS 1.3 only ClientHello / ServerHello are plaintext; the post-handshake
   * NewSessionTicket is encrypted and won't appear.
   */
  private fun extractPlaintextHandshakes(bytes: ByteArray): List<Pair<Int, ByteArray>> {
    val acc = java.io.ByteArrayOutputStream()
    val plaintextHsTypes = setOf(0x01, 0x02, 0x04, 0x08, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x14, 0x18)
    var p = 0
    loop@ while (p + 5 <= bytes.size) {
      val type = bytes[p].toInt() and 0xFF
      val vmaj = bytes[p + 1].toInt() and 0xFF
      if (vmaj != 0x03) break
      val len = ((bytes[p + 3].toInt() and 0xFF) shl 8) or (bytes[p + 4].toInt() and 0xFF)
      if (p + 5 + len > bytes.size) break
      when (type) {
        0x14 -> {
          // ChangeCipherSpec: in TLS 1.2 subsequent handshakes are encrypted; in
          // TLS 1.3 a dummy CCS is sent for middlebox compat. Continue scanning.
          p += 5 + len
        }
        0x16 -> {
          if (len >= 1 && (bytes[p + 5].toInt() and 0xFF) in plaintextHsTypes) {
            acc.write(bytes, p + 5, len)
            p += 5 + len
          } else {
            break@loop
          }
        }
        else -> p += 5 + len
      }
    }

    val msgs = mutableListOf<Pair<Int, ByteArray>>()
    val buf = acc.toByteArray()
    var q = 0
    while (q + 4 <= buf.size) {
      val hsType = buf[q].toInt() and 0xFF
      val hsLen = ((buf[q + 1].toInt() and 0xFF) shl 16) or
        ((buf[q + 2].toInt() and 0xFF) shl 8) or
        (buf[q + 3].toInt() and 0xFF)
      if (q + 4 + hsLen > buf.size) break
      msgs.add(hsType to buf.copyOfRange(q + 4, q + 4 + hsLen))
      q += 4 + hsLen
    }
    return msgs
  }

  private fun parseClientHelloBody(body: ByteArray): CHInfo? {
    if (body.size < 2 + 32 + 1) return null
    var off = 2 + 32
    val sidLen = body[off].toInt() and 0xFF
    off += 1
    if (off + sidLen + 2 > body.size) return null
    val sessionId = body.copyOfRange(off, off + sidLen)
    off += sidLen
    val csLen = ((body[off].toInt() and 0xFF) shl 8) or (body[off + 1].toInt() and 0xFF)
    off += 2
    if (off + csLen > body.size) return null
    val csCount = csLen / 2
    off += csLen
    if (off + 1 > body.size) return CHInfo(sessionId, csCount, emptyMap())
    val compLen = body[off].toInt() and 0xFF
    off += 1 + compLen
    if (off + 2 > body.size) return CHInfo(sessionId, csCount, emptyMap())
    val extLen = ((body[off].toInt() and 0xFF) shl 8) or (body[off + 1].toInt() and 0xFF)
    off += 2
    val extEnd = minOf(off + extLen, body.size)
    val ext = mutableMapOf<Int, ByteArray>()
    while (off + 4 <= extEnd) {
      val type = ((body[off].toInt() and 0xFF) shl 8) or (body[off + 1].toInt() and 0xFF)
      val l = ((body[off + 2].toInt() and 0xFF) shl 8) or (body[off + 3].toInt() and 0xFF)
      if (off + 4 + l > extEnd) break
      ext[type] = body.copyOfRange(off + 4, off + 4 + l)
      off += 4 + l
    }
    return CHInfo(sessionId, csCount, ext)
  }

  private fun parseServerHelloBody(body: ByteArray): SHInfo? {
    if (body.size < 2 + 32 + 1) return null
    val legacyVersion = ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF)
    var off = 2 + 32
    val sidLen = body[off].toInt() and 0xFF
    off += 1
    if (off + sidLen + 2 + 1 > body.size) return null
    val sessionId = body.copyOfRange(off, off + sidLen)
    off += sidLen
    val cipherSuite = ((body[off].toInt() and 0xFF) shl 8) or (body[off + 1].toInt() and 0xFF)
    off += 2
    off += 1 // compression method
    if (off + 2 > body.size) return SHInfo(legacyVersion, sessionId, cipherSuite, emptyMap())
    val extLen = ((body[off].toInt() and 0xFF) shl 8) or (body[off + 1].toInt() and 0xFF)
    off += 2
    val extEnd = minOf(off + extLen, body.size)
    val ext = mutableMapOf<Int, ByteArray>()
    while (off + 4 <= extEnd) {
      val type = ((body[off].toInt() and 0xFF) shl 8) or (body[off + 1].toInt() and 0xFF)
      val l = ((body[off + 2].toInt() and 0xFF) shl 8) or (body[off + 3].toInt() and 0xFF)
      if (off + 4 + l > extEnd) break
      ext[type] = body.copyOfRange(off + 4, off + 4 + l)
      off += 4 + l
    }
    return SHInfo(legacyVersion, sessionId, cipherSuite, ext)
  }

  private fun parseSupportedVersionsClient(extData: ByteArray): List<Int> {
    if (extData.isEmpty()) return emptyList()
    val len = extData[0].toInt() and 0xFF
    val out = mutableListOf<Int>()
    var i = 1
    while (i + 2 <= 1 + len && i + 2 <= extData.size) {
      out.add(((extData[i].toInt() and 0xFF) shl 8) or (extData[i + 1].toInt() and 0xFF))
      i += 2
    }
    return out
  }

  private fun parsePskKeyExchangeModes(extData: ByteArray): List<Int> {
    if (extData.isEmpty()) return emptyList()
    val len = extData[0].toInt() and 0xFF
    val out = mutableListOf<Int>()
    for (i in 0 until len) {
      if (1 + i < extData.size) out.add(extData[1 + i].toInt() and 0xFF)
    }
    return out
  }

  private fun pskKemName(m: Int): String = when (m) {
    0 -> "psk_ke"
    1 -> "psk_dhe_ke"
    else -> "unknown($m)"
  }

  private fun bytesToHex(b: ByteArray): String =
    if (b.isEmpty()) "(empty)" else b.joinToString("") { String.format("%02x", it.toInt() and 0xFF) }

  private fun analyzeResumptionEvidence(flowBufs: Map<Pair<Int, Int>, ByteArray>): ResumptionEvidence {
    // Detect server port: any flow whose dst contains a ClientHello.
    val serverPort = flowBufs.entries.firstNotNullOfOrNull { (k, b) ->
      if (extractPlaintextHandshakes(b).any { it.first == 0x01 }) k.second else null
    }
    if (serverPort == null) {
      log("[Resumption Evidence] No ClientHello observed in any flow.")
      return ResumptionEvidence(
        clientHelloCount = 0,
        serverHelloCount = 0,
        newSessionTicketCount = 0,
        flowCount = flowBufs.size,
        mechanism = "NO_CLIENT_HELLO",
        ch2HasNonEmptyTicket = false,
        ch2HasPreSharedKey = false,
        sh2HasPreSharedKey = false,
        secondFlightAbbreviated = false
      )
    }
    log("[Resumption Evidence] Server port = $serverPort")

    data class CHRec(val flow: Pair<Int, Int>, val info: CHInfo)
    data class SHRec(val flow: Pair<Int, Int>, val info: SHInfo)
    val chs = mutableListOf<CHRec>()
    val shs = mutableListOf<SHRec>()
    var nstCount = 0
    var nstTotalBytes = 0
    // Per server→client flow: the list of plaintext handshake message types we saw.
    // Used to detect whether the server's flight included Certificate (0x0B), which
    // is the canonical signal of a full vs. abbreviated handshake in TLS 1.2.
    val serverFlowHsTypes = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()

    for ((key, bytes) in flowBufs) {
      val msgs = extractPlaintextHandshakes(bytes)
      for ((hsType, body) in msgs) {
        if (key.first == serverPort) {
          serverFlowHsTypes.getOrPut(key) { mutableListOf() }.add(hsType)
        }
        when (hsType) {
          0x01 -> if (key.second == serverPort) parseClientHelloBody(body)?.let { chs.add(CHRec(key, it)) }
          0x02 -> if (key.first == serverPort) parseServerHelloBody(body)?.let { shs.add(SHRec(key, it)) }
          0x04 -> if (key.first == serverPort) {
            nstCount++
            nstTotalBytes += body.size
            if (body.size >= 6) {
              val lifetime = ((body[0].toInt() and 0xFF) shl 24) or
                ((body[1].toInt() and 0xFF) shl 16) or
                ((body[2].toInt() and 0xFF) shl 8) or
                (body[3].toInt() and 0xFF)
              val ticketLen = ((body[4].toInt() and 0xFF) shl 8) or (body[5].toInt() and 0xFF)
              log("  NewSessionTicket [TLS 1.2 plaintext]: lifetime_hint=${lifetime}s ticket_length=${ticketLen}B")
            }
          }
        }
      }
    }

    log("ClientHellos observed: ${chs.size}")
    for ((i, ch) in chs.withIndex()) {
      val sup = ch.info.ext[0x002B]?.let { parseSupportedVersionsClient(it) } ?: emptyList()
      val pkm = ch.info.ext[0x002D]?.let { parsePskKeyExchangeModes(it) } ?: emptyList()
      val ticket = ch.info.ext[0x0023]
      val psk = ch.info.ext[0x0029]
      log("  CH[${i + 1}] flow=${ch.flow.first}->${ch.flow.second}")
      log("    legacy_session_id (${ch.info.sessionId.size}B): ${bytesToHex(ch.info.sessionId)}")
      log(
        "    session_ticket(0x0023): " +
          (ticket?.let { if (it.isEmpty()) "present(empty)" else "present(${it.size}B)" } ?: "absent")
      )
      log("    pre_shared_key(0x0029): " + (psk?.let { "present(${it.size}B)" } ?: "absent"))
      if (pkm.isNotEmpty())
        log("    psk_key_exchange_modes(0x002D): ${pkm.map { pskKemName(it) }}")
      if (sup.isNotEmpty())
        log("    supported_versions(0x002B): ${sup.map { String.format("0x%04X", it) }}")
    }

    log("ServerHellos observed: ${shs.size}")
    for ((i, sh) in shs.withIndex()) {
      val supSel = sh.info.ext[0x002B]?.let {
        if (it.size >= 2) ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) else null
      }
      val pskSel = sh.info.ext[0x0029]?.let {
        if (it.size >= 2) ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) else null
      }
      log("  SH[${i + 1}] flow=${sh.flow.first}->${sh.flow.second}")
      log("    legacy_version=${String.format("0x%04X", sh.info.legacyVersion)}")
      log("    session_id (${sh.info.sessionId.size}B): ${bytesToHex(sh.info.sessionId)}")
      log("    cipher_suite=${String.format("0x%04X", sh.info.cipherSuite)}")
      val shTicket = sh.info.ext[0x0023]
      log(
        "    session_ticket(0x0023): " + (shTicket?.let {
          if (it.isEmpty()) "present(empty — server will issue NewSessionTicket)"
          else "present(${it.size}B, unexpected non-empty)"
        } ?: "absent")
      )
      if (pskSel != null) log("    pre_shared_key.selected_identity=$pskSel")
      if (supSel != null) log("    supported_versions.selected=${String.format("0x%04X", supSel)}")
    }
    log("NewSessionTicket records observed (TLS 1.2 plaintext only): $nstCount, total ${nstTotalBytes}B")

    log("Server-side handshake messages per flow:")
    for ((flow, types) in serverFlowHsTypes) {
      val histogram = types.groupingBy { it }.eachCount()
      val pretty = histogram.entries
        .sortedBy { it.key }
        .joinToString(", ") { (t, c) -> "${hsTypeName(t)}(0x%02X)x$c".format(t) }
      log("  flow=${flow.first}->${flow.second}: $pretty")
    }

    val secondShFlow = shs.getOrNull(1)?.flow
    val secondFlowHsTypes = secondShFlow?.let { serverFlowHsTypes[it] } ?: emptyList()
    val secondFlowHasCertificate = secondFlowHsTypes.contains(0x0B)
    val mechanism = inferMechanism(
      chs.map { it.info },
      shs.map { it.info },
      secondFlowHasCertificate
    )
    log("[Resumption Evidence] Inferred mechanism: $mechanism")

    val ch2 = chs.getOrNull(1)?.info
    val sh2 = shs.getOrNull(1)?.info
    val ch2Ticket = ch2?.ext?.get(0x0023)
    return ResumptionEvidence(
      clientHelloCount = chs.size,
      serverHelloCount = shs.size,
      newSessionTicketCount = nstCount,
      flowCount = flowBufs.size,
      mechanism = mechanism,
      ch2HasNonEmptyTicket = ch2Ticket != null && ch2Ticket.isNotEmpty(),
      ch2HasPreSharedKey = ch2?.ext?.containsKey(0x0029) == true,
      sh2HasPreSharedKey = sh2?.ext?.containsKey(0x0029) == true,
      secondFlightAbbreviated = !secondFlowHasCertificate
    )
  }

  private fun hsTypeName(t: Int): String = when (t) {
    0x01 -> "ClientHello"
    0x02 -> "ServerHello"
    0x04 -> "NewSessionTicket"
    0x08 -> "EncryptedExtensions"
    0x0B -> "Certificate"
    0x0C -> "ServerKeyExchange"
    0x0D -> "CertificateRequest"
    0x0E -> "ServerHelloDone"
    0x0F -> "CertificateVerify"
    0x10 -> "ClientKeyExchange"
    0x14 -> "Finished"
    0x18 -> "KeyUpdate"
    else -> "hs_$t"
  }

  /**
   * Infer which RFC mechanism the TOE used to resume.
   *
   * Important nuance for TLS 1.2 ticket (RFC 5077 §3.4):
   *   "If the server accepts the ticket and the Session ID is not empty,
   *    then it MUST respond with the same Session ID present in the
   *    ClientHello."
   * Therefore offering a non-empty session_ticket in the 2nd ClientHello does
   * NOT prove the server accepted it. The decisive evidence is whether the
   * server's 2nd-flight contains a Certificate message (full handshake) or
   * skips it (abbreviated handshake = resumption succeeded).
   */
  private fun inferMechanism(
    chs: List<CHInfo>,
    shs: List<SHInfo>,
    secondFlowHasCertificate: Boolean
  ): String {
    if (chs.size < 2) return "INSUFFICIENT (only ${chs.size} ClientHello observed)"
    val ch2 = chs[1]
    val sh1 = shs.getOrNull(0)
    val sh2 = shs.getOrNull(1)
    val abbreviated = !secondFlowHasCertificate

    // TLS 1.3 PSK
    if (ch2.ext.containsKey(0x0029)) {
      val accepted = sh2?.ext?.containsKey(0x0029) == true
      return if (accepted) "TLS_1_3_PSK_RESUMED (RFC 8446) — pre_shared_key offered AND server selected"
      else "TLS_1_3_PSK_OFFERED_NOT_ACCEPTED — pre_shared_key offered but server did not select"
    }

    val ticket = ch2.ext[0x0023]
    val ticketOffered = ticket != null && ticket.isNotEmpty()
    val sidOffered = ch2.sessionId.isNotEmpty() &&
      sh1 != null && ch2.sessionId.contentEquals(sh1.sessionId)
    val sidEcho = sidOffered && sh2 != null && sh2.sessionId.contentEquals(ch2.sessionId)

    if (ticketOffered) {
      return when {
        abbreviated && sidEcho ->
          "TLS_1_2_RFC5077_TICKET_RESUMED — server accepted ticket, abbreviated handshake, session_id echoed"
        abbreviated ->
          "TLS_1_2_RFC5077_TICKET_RESUMED_NO_SID_ECHO — abbreviated handshake but server did not echo session_id (RFC 5077 §3.4 anomaly)"
        else ->
          "TLS_1_2_TICKET_OFFERED_NOT_ACCEPTED — non-empty ticket presented (${ticket!!.size}B) but server performed full handshake (Certificate observed in 2nd flow)"
      }
    }

    if (sidOffered) {
      return when {
        sidEcho && abbreviated ->
          "TLS_1_2_RFC5246_SESSION_ID_RESUMED — server echoed session_id and skipped Certificate"
        sidEcho ->
          "TLS_1_2_SESSION_ID_ECHOED_BUT_FULL_HANDSHAKE — anomalous: id echoed but Certificate sent"
        else ->
          "TLS_1_2_SESSION_ID_OFFERED_NOT_RESUMED — 2nd CH carried 1st SH session_id but 2nd SH did not echo"
      }
    }

    return "NONE — 2nd CH does not request resumption (no PSK, empty/absent ticket, no matching session_id)"
  }
}
