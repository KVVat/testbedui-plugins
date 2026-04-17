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

@SFR("FCS_TLS_EXT.1", """
The TSF shall provide a communication channel between itself and another trusted 
IT product that is logically distinct from other communication channels and provides 
assured identification of its end points and protection of the channel data from 
modification or disclosure.
""","network")
class FcsTlsExt1Test {

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

  // Mapping of some common cipher suites to their hex strings for verification
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
      // Clean up pcap on device
      // client.execute(ShellCommandRequest("su 0 rm /data/local/tmp/traffic.pcap"), adb.deviceSerial)
    }
  }

  @Test
  fun testTlsCipherSuites(){
    val hostName = "https://tls-v1-2.badssl.com:1012/"
    val resp = tlsCapturePacket("normal", hostName)
    val httpret = resp.first
    println("[JUnit] HTTP response: $httpret")
    
    val pcapPath = resp.second
    analyzePcap(pcapPath)
  }

  private fun tlsCapturePacket(testlabel:String, testurl:String): Pair<String, Path> {
    var pcap: Path = Paths.get("/")
    var httpResp: String = ""
    val serial = adb.deviceSerial

    runBlocking {
      // Install helper app
      val browserApk = File(JUnitBridge.resourceDir, "openurl-debug.apk")
      val ret = AdamUtils.installApk(client, serial, browserApk, true)
      Assert.assertTrue("Failed to install openurl app: ${ret}", ret.startsWith("Success"))

      // 1. Start tcpdump in background via coroutine
      // Assumes adb root or su is available
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

      // 2. Launch action (e.g., open URL)
      // Assuming com.example.openurl is available as in original test
      client.execute(ShellCommandRequest("am force-stop com.example.openurl"), serial)
      Thread.sleep(500) // Give it time to stop
      client.execute(ShellCommandRequest(
        "am start -a android.intent.action.VIEW -n com.example.openurl/.MainActivity"+
                " -e openurl $testurl"
      ), serial)

      // 3. Wait for action to send Client Hello (instead of waiting for log line)
      Thread.sleep(5000)
      httpResp = "Skipped waiting for logcat"

      // 4. Stop tcpdump
      client.execute(ShellCommandRequest("su 0 sh -c \"pkill tcpdump\""), serial)
      tcpdumpJob.cancel() // Cancel coroutine just in case
      Thread.sleep(2000) // Give it time to flush

      // 5. Pull pcap file
      val src = "/data/local/tmp/traffic.pcap"
      
      // Wait up to 5 seconds for file to appear and have data
      var fileReady = false
      for (i in 1..5) {
          val result = client.execute(ShellCommandRequest("su 0 sh -c \"test -s $src && echo yes || echo no\""), serial)
          val exists = String(result.stdout).trim()
          println("[JUnit] Attempt $i: pcap file exists and not empty = $exists")
          if (exists == "yes") {
              fileReady = true
              break
          }
          Thread.sleep(1000)
      }
      
      if (!fileReady) {
          // Log tcpdump.out content to see why it failed
          val result = client.execute(ShellCommandRequest("su 0 cat /data/local/tmp/tcpdump.out"), serial)
          val tcpdumpOut = String(result.stdout)
          println("[JUnit] tcpdump.out content:\n$tcpdumpOut")
          Assert.fail("traffic.pcap not found or empty. tcpdump output: $tcpdumpOut")
      }
      
      val pcap0: Path = kotlin.io.path.createTempFile("t", ".pcap")
      AdamUtils.pullFile(src, pcap0.toString(), adb, true)
      
      pcap = copyPcapToOutPath(pcap0, testlabel)
    }
    return Pair(httpResp, pcap)
  }

  private fun analyzePcap(pcapPath: Path) {
    println("[JUnit] Analyzing pcap using pkts.io...")
    
    val pcap = Pcap.openStream(pcapPath.toFile())
    var foundClientHello = false
    val supportedCiphers = mutableListOf<Int>()
    
    var packetCount = 0
    pcap.loop { packet ->
        packetCount++
        val hasTcp = packet.hasProtocol(Protocol.TCP)
        if (packetCount <= 10) {
            println("[JUnit] Packet $packetCount: hasTCP=$hasTcp")
        }
        if (hasTcp) {
            val tcp = packet.getPacket(Protocol.TCP) as TCPPacket
            val payload = tcp.payload
            if (payload != null) {
                if (packetCount <= 10) {
                    println("[JUnit] Packet $packetCount payload size: ${payload.capacity()}")
                }
                val bytes = ByteArray(payload.capacity().toInt())
                for (i in 0 until bytes.size) {
                    bytes[i] = payload.getByte(i)
                }
                
                for (i in 0 until bytes.size - 5) {
                    // Search for TLS Handshake (0x16) and Version (0x03)
                    if (bytes[i] == 0x16.toByte() && bytes[i+1] == 0x03.toByte()) {
                        if (i + 5 < bytes.size && bytes[i+5] == 0x01.toByte()) {
                            foundClientHello = true
                            
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
                            }
                            return@loop false // Stop loop
                        }
                    }
                }
            }
        }
        true // Continue loop
    }
    
    Assert.assertTrue("Client Hello not found in capture", foundClientHello)
    println("Supported Ciphers found: ${supportedCiphers.map { String.format("0x%04X", it) }}")

    val requiredCodes = REQUIRED_CIPHERS_IN_SFR.mapNotNull { CIPHER_MAP[it] }
    val matches = supportedCiphers.intersect(requiredCodes)
    
    println("Matches with required ciphers: ${matches.map { String.format("0x%04X", it) }}")
    Assert.assertTrue("No required ciphers found in Client Hello", matches.isNotEmpty())
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
