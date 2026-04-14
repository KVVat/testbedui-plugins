package org.example.plugin.ftpitc

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
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

/**
 * FCS_TLSC_EXT.1 TLS Client Protocol
 * Verify that the TSF implements TLS 1.2 or TLS 1.3 as a client.
 */
@SFR("FCS_TLSC_EXT.1", """
The TSF shall implement [selection: TLS 1.2 (RFC 5246), TLS 1.3 (RFC 8446)] as a client
that supports additional functionality for session renegotiation protection and
abort attempts by a server to negotiate any TLS or SSL version prior to TLS 1.2.
""")
class FcsTlscExtTest {

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
  fun testNormalHost() {
    val hostName = "https://tls-v1-2.badssl.com:1012/"
    val resp = tlsCapturePacket("normal", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = false)
  }

  @Test
  fun testTls10Reject() {
    val hostName = "https://tls-v1-0.badssl.com:1010/"
    val resp = tlsCapturePacket("tls10", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = false)
  }

  @Test
  fun testTls11Reject() {
    val hostName = "https://tls-v1-1.badssl.com:1011/"
    val resp = tlsCapturePacket("tls11", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = false)
  }

  @Test
  fun testNullCipherReject() {
    val hostName = "https://null.badssl.com/"
    val resp = tlsCapturePacket("nullcipher", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = false)
  }

  @Test
  fun test3DesReject() {
    val hostName = "https://3des.badssl.com/"
    val resp = tlsCapturePacket("3des", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = false)
  }

  @Test
  fun testRc4Reject() {
    val hostName = "https://rc4.badssl.com/"
    val resp = tlsCapturePacket("rc4", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should not be 200 (rejected)"), httpret == "200", IsEqual(false))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = false)
  }

  @Test
  fun testExpiredHost() {
    val hostName = "https://expired.badssl.com/"
    val resp = tlsCapturePacket("expired", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should be 525 or error"), httpret, IsEqual("525"))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = true)
  }

  @Test
  fun testInvalidHost() {
    val hostName = "https://wrong.host.badssl.com/"
    val resp = tlsCapturePacket("invalid", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should be 526 or error"), httpret, IsEqual("526"))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = true)
  }

  @Test
  fun testMutualAuthNoCert() {
    val hostName = "https://tls-v1-2.badssl.com:1012/"
    val resp = tlsCapturePacket("nocert", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = false, expectClientCert = false)
  }

  @Test
  fun testMutualAuthWithCert() {
    val hostName = "https://client.badssl.com/"
    val p12Path = "/data/data/com.example.openurl/files/badssl.com-client.p12"
    val p12Pass = "badssl.com"
    
    // Push cert from resources to device
    val file1 = File("apps/openurl/resource/badssl.com-client.p12")
    val file2 = File("../apps/openurl/resource/badssl.com-client.p12")
    val file3 = File("../testbedui-plugins/apps/openurl/resource/badssl.com-client.p12")
    val file4 = File("../../testbedui-plugins/apps/openurl/resource/badssl.com-client.p12")
    val certFile = if (file1.exists()) file1 else if (file2.exists()) file2 else if (file3.exists()) file3 else file4
    Assert.assertTrue("Certificate file not found in resources. Checked:\n${file1.absolutePath}\n${file2.absolutePath}\n${file3.absolutePath}\n${file4.absolutePath}", certFile.exists())

    val serial = adb.deviceSerial
    val process = ProcessBuilder("adb", "-s", serial, "push", certFile.absolutePath, "/sdcard/").start()
    val exitCode = process.waitFor()
    Assert.assertEquals("Failed to push certificate via adb", 0, exitCode)

    // Copy to app private directory
    runBlocking {
      client.execute(com.malinskiy.adam.request.shell.v1.ShellCommandRequest("su 0 mkdir -p /data/data/com.example.openurl/files/"), serial)
      client.execute(com.malinskiy.adam.request.shell.v1.ShellCommandRequest("su 0 cp /sdcard/badssl.com-client.p12 /data/data/com.example.openurl/files/"), serial)
      client.execute(com.malinskiy.adam.request.shell.v1.ShellCommandRequest("su 0 chmod 666 /data/data/com.example.openurl/files/badssl.com-client.p12"), serial)
    }

    val resp = tlsCapturePacket("withcert", hostName, p12Path, p12Pass)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.second
    analyzePcap(pcapPath, expectAlert = false, expectClientCert = true)
  }

  @Test
  fun testSessionResumption() {
    val hostName = "https://tls-v1-2.badssl.com:1012/"
    val resp = tlsCapturePacket("resumption", hostName, resumption = true)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    errs.checkThat(a.msg("HTTP response should start with 200"), httpret.startsWith("200"), IsEqual(true))
    
    val pcapPath = resp.second
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
    var tlsVersion: Int? = null

    pcap.loop { packet ->
      if (packet.hasProtocol(Protocol.TCP)) {
        val tcp = packet.getPacket(Protocol.TCP) as TCPPacket
        val payload = tcp.payload
        if (payload != null) {
          val bytes = payload.array
          
          // Search for TLS Record
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
                        extCurrent += 4 + extDataLen
                      }
                    }
                  }
                }
              }
              
              if (hsType == 0x0b) { // Certificate
                certMessageCount++
                if (certMessageCount == 2) {
                  foundClientCert = true
                  println("[JUnit] Found Client Certificate packet (2nd cert message)!")
                } else {
                  println("[JUnit] Found Server Certificate packet (1st cert message)!")
                }
              }
            }
            
            // Check for Alert (0x15)
            if (contentType == 0x15 && versionMajor == 0x03) {
              foundAlert = true
              println("[JUnit] Found Alert packet!")
            }
          }
        }
      }
      true // Continue loop
    }

    Assert.assertTrue("Client Hello not found in capture", foundClientHello)
    println("[JUnit] Supported Ciphers found: ${supportedCiphers.map { String.format("0x%04X", it) }}")
    println("[JUnit] Found Extensions: ${foundExtensions.map { String.format("0x%04X", it) }}")
    println("[JUnit] TLS Version in Client Hello: ${tlsVersion?.let { String.format("0x%04X", it) }}")

    // SFR: FCS_TLSC_EXT.1.2 Ciphersuites
    val requiredCodes = REQUIRED_CIPHERS_IN_SFR.mapNotNull { CIPHER_MAP[it] }
    val matches = supportedCiphers.intersect(requiredCodes)
    println("[JUnit] Matches with required ciphers: ${matches.map { String.format("0x%04X", it) }}")
    Assert.assertTrue("No required ciphers found in Client Hello", matches.isNotEmpty())
    
    // SFR: FCS_TLSC_EXT.1.3 Forbidden Ciphers (Null, RC4, 3DES)
    val forbiddenCiphers = setOf(
      0x0000, // TLS_NULL_WITH_NULL_NULL
      0x0004, // TLS_RSA_WITH_RC4_128_MD5
      0x0005, // TLS_RSA_WITH_RC4_128_SHA
      0x000A, // TLS_RSA_WITH_3DES_EDE_CBC_SHA
      0x000D  // TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA
    )
    val forbiddenMatches = supportedCiphers.intersect(forbiddenCiphers)
    Assert.assertTrue("Forbidden ciphers found in Client Hello: ${forbiddenMatches.map { String.format("0x%04X", it) }}", forbiddenMatches.isEmpty())

    // SFR: FCS_TLSC_EXT.1.1 Support TLS 1.2 or 1.3
    Assert.assertTrue("TLS version should be 1.2 or later", tlsVersion != null && tlsVersion >= 0x0303)

    // SFR: FCS_TLSC_EXT.1.4 Required Extensions
    Assert.assertTrue("signature_algorithms extension not found", foundExtensions.contains(0x000D))
    Assert.assertTrue("supported_groups extension not found", foundExtensions.contains(0x000A))

    // SFR: FCS_TLSC_EXT.4.1 Secure Renegotiation (Test 1.1)
    val hasSCSV = supportedCiphers.contains(0x00FF)
    val hasRenegInfo = foundExtensions.contains(0xFF01)
    println("[JUnit] Has TLS_EMPTY_RENEGOTIATION_INFO_SCSV: $hasSCSV")
    println("[JUnit] Has renegotiation_info extension: $hasRenegInfo")
    Assert.assertTrue("Neither SCSV nor renegotiation_info extension found in Client Hello", hasSCSV || hasRenegInfo)

    // SFR: FCS_TLSC_EXT.5.1 Session Resumption (Offered support)
    val hasSessionTicket = foundExtensions.contains(0x0023)
    println("[JUnit] Has SessionTicket extension: $hasSessionTicket")
    Assert.assertTrue("SessionTicket extension not found in Client Hello", hasSessionTicket)

    // SFR: FCS_TLSC_EXT.6.1 TLS 1.3 Resumption Refinements
    Assert.assertTrue("psk_key_exchange_modes extension not found", foundExtensions.contains(0x002D))

    // SFR: FCS_TLSC_EXT.6.2 No Early Data
    Assert.assertFalse("early_data extension should NOT be present", foundExtensions.contains(0x002A))

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
      println("[JUnit] Found $clientHelloCount Client Hellos, confirmed resumption attempt!")
    }
  }

  private fun tlsCapturePacket(testlabel:String, testurl:String, p12Path: String? = null, p12Pass: String? = null, resumption: Boolean = false): Pair<String, Path> {
    var pcap: Path = Paths.get("/")
    var httpResp: String = ""
    val serial = adb.deviceSerial

    runBlocking {
      val browserApk = File("/Users/wkouki/AndroidStudioProjects/testbedui-plugins/apps/openurl/build/outputs/apk/debug/openurl-debug.apk")
      val ret = AdamUtils.installApk(client, serial, browserApk, true)
      Assert.assertTrue("Failed to install openurl app: ${ret}", ret.startsWith("Success"))

      val tcpdumpJob = launch(Dispatchers.IO) {
          try {
              println("[JUnit] Starting tcpdump in coroutine...")
              client.execute(ShellCommandRequest("su 0 tcpdump -i any -U -w /data/local/tmp/traffic.pcap"), serial)
              println("[JUnit] tcpdump coroutine finished")
          } catch (e: Exception) {
              println("[JUnit] tcpdump coroutine exception: ${e.message}")
          }
      }
      Thread.sleep(2000) // Give tcpdump time to start

      client.execute(ShellCommandRequest("am force-stop com.example.openurl"), serial)
      Thread.sleep(500)
      
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
        println("[JUnit] worker@return => ${res.text}")
        val text = res.text
        val marker = "worker@return:"
        val index = text.lastIndexOf(marker)
        if (index >= 0) {
          httpResp = text.substring(index + marker.length).trim()
        } else {
          httpResp = text.trim()
        }
      } else {
        println("[JUnit] we can't grab the return value from worker.")
        httpResp = "Error: timeout waiting for logcat"
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
    return Pair(httpResp, pcap)
  }

  val OUT_PATH  = "../results/capture/"
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
