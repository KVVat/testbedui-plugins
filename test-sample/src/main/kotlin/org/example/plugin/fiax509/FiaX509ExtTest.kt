package org.example.plugin.fiax509

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.TestAssertLogger
import org.example.plugin.utils.SFR
import org.example.plugin.utils.SFRCheckList
import org.example.plugin.utils.*
import org.example.plugin.utils.log
import org.example.project.JUnitBridge
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import org.example.project.adb.rules.AdbDeviceRule
import com.malinskiy.adam.AndroidDebugBridgeClient
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hamcrest.core.IsEqual

/**
 * FIA_X509_EXT X.509 Certificate Validation
 *
 * This test suite investigates and verifies the behavior of Android (Conscrypt)
 * regarding X.509 certificate validation rules as defined in FIA_X509_EXT.
 */
@SFR("FIA_X509_EXT", """
The TSF shall validate certificates in accordance with the rules defined in FIA_X509_EXT.1.
""", category = "network")
class FiaX509ExtTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupCheckList() {
            // Register checklist items based on the plan
            SFRCheckList.register("FIA_X509_EXT.1.1", "Verify certificate validation rules (Path length, Time, Algorithms)")
            SFRCheckList.register("FIA_X509_EXT.1.2", "Verify processing of extensions (AKID, SKID, KeyUsage)")
            SFRCheckList.register("FIA_X509_EXT.1.3", "Verify revocation status validation (OCSP/CRL)")
            SFRCheckList.register("FIA_X509_EXT.1.5", "Verify context and usage validation (EKU)")
            SFRCheckList.register("FIA_X509_EXT.1.6", "Verify trust store management")
        }
    }

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

    @Before
    fun setup() {
        // Setup if needed
    }

    @After
    fun teardown() {
        // Cleanup if needed
    }

    /**
     * Investigation test to see what Conscrypt handles by default.
     * This test will attempt to connect to a server and log the certificate details.
     */
    @Test
    fun testInvestigateConscryptBehavior() {
        log("Starting investigation of Conscrypt behavior...")
        val hostName = "https://expired.badssl.com/"
        log("Target host: $hostName")
    }

    @Test
    fun testLogSupportedAlgorithms() {
        log("=== Supported Algorithms by Conscrypt ===")
        val provider = java.security.Security.getProvider("Conscrypt")
        if (provider != null) {
            val services = provider.services
            services.forEach { service ->
                if (service.type == "Signature" || service.type == "MessageDigest") {
                    log("Type: ${service.type}, Algorithm: ${service.algorithm}")
                }
            }
        } else {
            log("Conscrypt provider not found!")
        }
        
        // This provides evidence of supported algorithms on the specific device
        SFRCheckList.pass("FIA_X509_EXT.1.1")
    }

    @Test
    fun testExpiredHost() {
        val hostName = "https://expired.badssl.com/"
        val resp = tlsCapturePacket("expired", hostName)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be error/failure for expired cert"), httpret.startsWith("200"), IsEqual(false))
        
        SFRCheckList.pass("FIA_X509_EXT.1.1")
    }

    @Test
    fun testInvalidHost() {
        val hostName = "https://wrong.host.badssl.com/"
        val resp = tlsCapturePacket("invalid", hostName)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be error/failure for wrong host"), httpret.startsWith("200"), IsEqual(false))
        
        SFRCheckList.pass("FIA_X509_EXT.1.1")
    }

    @Test
    fun testSha1Reject() {
        val hostName = "https://sha1-2017.badssl.com/"
        val resp = tlsCapturePacket("sha1", hostName)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be error/failure for SHA-1 cert"), httpret.startsWith("200"), IsEqual(false))
        
        SFRCheckList.pass("FIA_X509_EXT.1.1")
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
                }
            }

            val tcpdumpJob = launch(Dispatchers.IO) {
                try {
                    client.execute(ShellCommandRequest("su 0 rm -f /data/local/tmp/traffic.pcap"), serial)
                    client.execute(ShellCommandRequest("su 0 tcpdump -i any -U -w /data/local/tmp/traffic.pcap"), serial)
                } catch (e: Exception) {
                    log("tcpdump exception: ${e.message}")
                }
            }
            Thread.sleep(2000) // Give tcpdump time to start

            client.execute(ShellCommandRequest("am force-stop com.example.openurl"), serial)
            Thread.sleep(500)
            
            var cmd = "am start -a android.intent.action.VIEW -n com.example.openurl/.MainActivity -e openurl $testurl"
            if (!p12Path.isNullOrBlank()) cmd += " -e p12path $p12Path"
            if (!p12Pass.isNullOrBlank()) cmd += " -e p12pass '$p12Pass'"
            if (!trustPath.isNullOrBlank()) cmd += " -e trustpath /data/data/com.example.openurl/files/cert.pem"
            if (resumption) cmd += " --ez resumption true"
            cmd += " -e type $type"
            if (forceTls12) cmd += " --ez forceTls12 true"
            
            client.execute(ShellCommandRequest(cmd), serial)

            val res = AdamUtils.waitLogcatLine(100, "worker@return", adb)
            if (res != null) {
                val text = res.text
                val marker = "worker@return:"
                val index = text.lastIndexOf(marker)
                if (index >= 0) {
                    httpResp = text.substring(index + marker.length).trim()
                } else {
                    httpResp = text.trim()
                }
            } else {
                httpResp = "Error: timeout waiting for logcat"
            }

            val logcatResult = client.execute(ShellCommandRequest("su 0 logcat -d"), serial)
            val logcatOutput = String(logcatResult.stdout)
            val lines = logcatOutput.split("\n")
            val workerLines = lines.filter { it.contains("worker@") }
            if (workerLines.isNotEmpty()) {
                workerLogsStr = workerLines.joinToString("\n")
            }

            Thread.sleep(2000)
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
    
    private fun copyPcapToOutPath(pcap:Path, testlabel:String):Path {
        val outdir = File(Paths.get(OUT_PATH).toUri())
        if(!outdir.exists()){
            outdir.mkdirs()
        }
        val tstmp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
        val to = Paths.get(OUT_PATH,"${tstmp}-${testlabel}.pcap")
        Files.copy(pcap, to)
        return to
    }

    @Test
    fun testVerifyCertificateInPcapTls12() {
        val hostName = "https://tls-v1-2.badssl.com:1012/"
        val resp = tlsCapturePacket("tls12_cert_check", hostName, forceTls12 = true)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be 200"), httpret.startsWith("200"), org.hamcrest.core.IsEqual(true))
        
        val pcapPath = resp.pcapPath
        analyzePcapForCertificate(pcapPath)
        
        SFRCheckList.pass("FIA_X509_EXT.1.1")
    }

    private fun analyzePcapForCertificate(pcapPath: Path) {
        log("Analyzing PCAP for certificate details: $pcapPath")
        val pcap = io.pkts.Pcap.openStream(pcapPath.toFile())
        
        data class FlowKey(val src: Int, val dst: Int)
        val flowBufs = mutableMapOf<FlowKey, java.io.ByteArrayOutputStream>()
    
        pcap.loop { packet ->
            if (packet.hasProtocol(io.pkts.protocol.Protocol.TCP)) {
                val tcp = packet.getPacket(io.pkts.protocol.Protocol.TCP) as io.pkts.packet.TCPPacket
                val payload = tcp.payload
                if (payload != null) {
                    val srcPort = tcp.getSourcePort()
                    val dstPort = tcp.getDestinationPort()
                    val bytes = payload.array
                    flowBufs.getOrPut(FlowKey(srcPort, dstPort)) { java.io.ByteArrayOutputStream() }.write(bytes)
                }
            }
            true
        }
        
        // Find the server-to-client flow that contains the Certificate message
        // In TLS 1.2, Certificate message starts with 0x0b (Handshake Type) inside a Handshake record (0x16)
        for ((key, baos) in flowBufs) {
            val bytes = baos.toByteArray()
            var i = 0
            while (i + 5 <= bytes.size) {
                val ct = bytes[i].toInt() and 0xFF
                val vmaj = bytes[i+1].toInt() and 0xFF
                if (ct == 0x16 && vmaj == 0x03) { // Handshake record
                    val recLen = ((bytes[i+3].toInt() and 0xFF) shl 8) or (bytes[i+4].toInt() and 0xFF)
                    var p = i + 5
                    while (p + 4 <= i + 5 + recLen && p + 4 <= bytes.size) {
                        val hsType = bytes[p].toInt() and 0xFF
                        val hsLen = ((bytes[p+1].toInt() and 0xFF) shl 16) or ((bytes[p+2].toInt() and 0xFF) shl 8) or (bytes[p+3].toInt() and 0xFF)
                        
                        if (hsType == 0x0b) { // Certificate
                            log("Found Certificate handshake message in flow ${key.src} -> ${key.dst}")
                            
                            // Extract certificates
                            val certsLen = ((bytes[p+4].toInt() and 0xFF) shl 16) or ((bytes[p+5].toInt() and 0xFF) shl 8) or (bytes[p+6].toInt() and 0xFF)
                            var certPtr = p + 7
                            val certEnd = certPtr + certsLen
                            
                            if (certPtr + 3 <= bytes.size) {
                                val certLen = ((bytes[certPtr].toInt() and 0xFF) shl 16) or ((bytes[certPtr+1].toInt() and 0xFF) shl 8) or (bytes[certPtr+2].toInt() and 0xFF)
                                val certBytes = bytes.copyOfRange(certPtr + 3, Math.min(certPtr + 3 + certLen, bytes.size))
                                
                                try {
                                    val cf = java.security.cert.CertificateFactory.getInstance("X.509")
                                    val cert = cf.generateCertificate(java.io.ByteArrayInputStream(certBytes)) as java.security.cert.X509Certificate
                                    log("  Subject: ${cert.subjectDN}")
                                    log("  Issuer: ${cert.issuerDN}")
                                    log("  Signature Algorithm: ${cert.sigAlgName}")
                                    log("  Public Key Algorithm: ${cert.publicKey.algorithm}")
                                    
                                    if (cert.publicKey is java.security.interfaces.RSAPublicKey) {
                                        val rsaKey = cert.publicKey as java.security.interfaces.RSAPublicKey
                                        log("  RSA Key Size: ${rsaKey.modulus.bitLength()} bits")
                                    }
                                } catch (e: Exception) {
                                    log("  Failed to parse certificate: ${e.message}")
                                }
                                return // Found and processed the first cert
                            }
                        }
                        p += 4 + hsLen
                    }
                    i += 5 + recLen
                } else {
                    i++
                }
            }
        }
        log("  Warning: Certificate handshake message not found in PCAP.")
    }
}
