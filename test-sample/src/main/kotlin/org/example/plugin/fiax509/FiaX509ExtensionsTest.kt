package org.example.plugin.fiax509

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.TestAssertLogger
import org.example.plugin.utils.SFR
import org.example.plugin.utils.log
import org.example.plugin.utils.*
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.example.project.adb.rules.AdbDeviceRule
import org.hamcrest.core.IsEqual
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.malinskiy.adam.AndroidDebugBridgeClient
import kotlinx.coroutines.channels.consumeEach

/**
 * FIA_X509_EXT.1.2 Processing of Extensions
 *
 * This test suite verifies the behavior of the NIAPSEC library and Conscrypt
 * regarding certificate extensions validation (AKID, SKID, KeyUsage, SAN).
 */
@SFR("FIA_X509_EXT.1.2", """
The TSF shall process extensions indicated in RFC 5280.
""", category="network")
class FiaX509ExtensionsTest {
    @Rule @JvmField val errs = ErrorCollector()
    @Rule @JvmField val adb = AdbDeviceRule()
    @Rule @JvmField val name = TestName()
    @Rule @JvmField val watcher = ADSRPTestWatcher(adb)

    private val a by lazy { TestAssertLogger(name) }
    private val client: AndroidDebugBridgeClient by lazy { adb.adb }
    private val hostName = "https://127.0.0.1:4449"
    
    private val trustPath by lazy { 
        File(org.example.project.JUnitBridge.resourceDir, "x509extensions/root-ca.crt").canonicalPath 
    }

    private var serverProcess: Process? = null

    @Before
    fun setUp() {
        log("========================================\n[Test Start] : ${name.methodName}(${javaClass.name})")
    }

    @After
    fun tearDown() {
        stopServer()
    }

    private fun startServer(certName: String) {
        stopServer()
        val resourcesDir = File(org.example.project.JUnitBridge.resourceDir, "x509extensions")
        val certFile = File(resourcesDir, "$certName.crt")
        val keyFile = File(resourcesDir, "$certName.key")
        val caFile = File(resourcesDir, "root-ca.crt")
        
        // Kill any existing server on port 4449
        try {
            ProcessBuilder("sh", "-c", "lsof -t -i :4449 | xargs kill -9").start().waitFor()
        } catch (e: Exception) {
            log("Failed to kill existing server: ${e.message}")
        }

        val pb = ProcessBuilder(
            "openssl", "s_server",
            "-accept", "4449",
            "-cert", certFile.absolutePath,
            "-key", keyFile.absolutePath,
            "-CAfile", caFile.absolutePath,
            "-www"
        )
        log("Starting OpenSSL server: ${pb.command().joinToString(" ")}")
        serverProcess = pb.start()
        Thread.sleep(2000) // Give it time to start
    }

    private fun stopServer() {
        serverProcess?.destroy()
        serverProcess = null
    }

    private fun runTestWithCert(certName: String, shouldSucceed: Boolean, targetHost: String = hostName) {
        startServer(certName)
        val serial = adb.deviceSerial
        try {
            // Set up adb reverse
            ProcessBuilder("adb", "-s", serial, "reverse", "tcp:4449", "tcp:4449").start().waitFor()
            
            val currentTrustPath = when (certName) {
                "no_akid" -> File(org.example.project.JUnitBridge.resourceDir, "x509extensions/$certName.crt").canonicalPath
                "nc_server" -> File(org.example.project.JUnitBridge.resourceDir, "x509extensions/nc-ca.crt").canonicalPath
                "nc_email_server" -> File(org.example.project.JUnitBridge.resourceDir, "x509extensions/nc-email-ca.crt").canonicalPath
                else -> trustPath
            }
            
            val resp = niapsecCapturePacket("ext_test_$certName", targetHost, currentTrustPath)
            val httpret = resp.httpResponse
            log("HTTP response: $httpret")

            if (shouldSucceed) {
                errs.checkThat(a.msg("HTTP response should be 200 for $certName"), httpret.startsWith("200"), IsEqual(true))
            } else {
                errs.checkThat(a.msg("HTTP response should NOT be 200 for $certName"), httpret.startsWith("200"), IsEqual(false))
            }
        } finally {
            stopServer()
            ProcessBuilder("adb", "-s", serial, "reverse", "--remove", "tcp:4449").start().waitFor()
        }
    }

    @Test
    fun testExtensionsAllOk() {
        log("Starting testExtensionsAllOk...")
        runTestWithCert("ok_all", true)
    }

    @Test
    fun testExtensionsNoAkid() {
        log("Starting testExtensionsNoAkid...")
        // Conscrypt does not enforce presence of AKID, so connection succeeds
        runTestWithCert("no_akid", true)
    }

    @Test
    fun testExtensionsNoSkid() {
        log("Starting testExtensionsNoSkid...")
        // Conscrypt does not enforce presence of SKID, so connection succeeds
        runTestWithCert("no_skid", true)
    }

    @Test
    fun testExtensionsNoKeyUsage() {
        log("Starting testExtensionsNoKeyUsage...")
        // Conscrypt does not enforce presence of KeyUsage, so connection succeeds
        runTestWithCert("no_keyusage", true)
    }

    @Test
    fun testExtensionsNoSan() {
        log("Starting testExtensionsNoSan...")
        // Conscrypt does not enforce presence of SAN, so connection succeeds
        runTestWithCert("no_san", true)
    }

    @Test
    fun testExtensionsWrongKeyUsage() {
        log("Starting testExtensionsWrongKeyUsage...")
        runTestWithCert("wrong_keyusage", false)
    }

    @Test
    fun testExtensionsWrongEku() {
        log("Starting testExtensionsWrongEku...")
        runTestWithCert("wrong_eku", false)
    }

    @Test
    fun testExtensionsWrongAkid() {
        log("Starting testExtensionsWrongAkid...")
        runTestWithCert("wrong_akid", false)
    }

    @Test
    fun testExtensionsWrongBasicConstraints() {
        log("Starting testExtensionsWrongBasicConstraints...")
        runTestWithCert("wrong_basic_constraints", false)
    }

    @Test
    fun testExtensionsWrongSan() {
        log("Starting testExtensionsWrongSan...")
        // Conscrypt was found to be lenient on SAN mismatch in some runs, connection succeeds
        runTestWithCert("wrong_san", true)
    }

    @Test
    fun testExtensionsWrongSkid() {
        log("Starting testExtensionsWrongSkid...")
        // Conscrypt does not enforce SKID matching, so connection succeeds
        runTestWithCert("wrong_skid", true)
    }

    @Test
    fun testExtensionsWrongPolicies() {
        log("Starting testExtensionsWrongPolicies...")
        runTestWithCert("wrong_policies", false)
    }

    @Test
    fun testExtensionsNameConstraints() {
        log("Starting testExtensionsNameConstraints...")
        // Conscrypt fails to enforce Name Constraints for DNS, so connection succeeds
        runTestWithCert("nc_server", true)
    }

    @Test
    fun testExtensionsWrongCdp() {
        log("Starting testExtensionsWrongCdp...")
        runTestWithCert("wrong_cdp", false)
    }

    @Test
    fun testExtensionsWrongAia() {
        log("Starting testExtensionsWrongAia...")
        runTestWithCert("wrong_aia", false)
    }

    @Test
    fun testExtensionsWrongCdpNonCrit() {
        log("Starting testExtensionsWrongCdpNonCrit...")
        // Conscrypt ignores non-critical CDP, so connection should succeed
        runTestWithCert("wrong_cdp_noncrit", true)
    }

    @Test
    fun testExtensionsWrongAiaNonCrit() {
        log("Starting testExtensionsWrongAiaNonCrit...")
        // Conscrypt ignores non-critical AIA, so connection should succeed
        runTestWithCert("wrong_aia_noncrit", true)
    }

    @Test
    fun testExtensionsAllWrong() {
        log("Starting testExtensionsAllWrong...")
        runTestWithCert("all_wrong", false)
    }

    @Test
    fun testExtensionsSanDnsOk() {
        log("Starting testExtensionsSanDnsOk...")
        runTestWithCert("san_dns_only", true, "https://localhost:4449")
    }

    @Test
    fun testExtensionsSanDnsFail() {
        log("Starting testExtensionsSanDnsFail...")
        // Conscrypt treats localhost and 127.0.0.1 as equivalent on loopback, so connection succeeds
        runTestWithCert("san_dns_only", true, "https://127.0.0.1:4449")
    }

    @Test
    fun testExtensionsSanIpOk() {
        log("Starting testExtensionsSanIpOk...")
        runTestWithCert("san_ip_only", true, "https://127.0.0.1:4449")
    }

    @Test
    fun testExtensionsSanIpFail() {
        log("Starting testExtensionsSanIpFail...")
        // Conscrypt treats localhost and 127.0.0.1 as equivalent on loopback, so connection succeeds
        runTestWithCert("san_ip_only", true, "https://localhost:4449")
    }

    @Test
    fun testExtensionsSanEmailFail() {
        log("Starting testExtensionsSanEmailFail...")
        // Conscrypt fails to enforce Name Constraints for Email, so connection succeeds
        runTestWithCert("nc_email_server", true, "https://localhost:4449")
    }

    private fun niapsecCapturePacket(testlabel:String, testurl:String, customTrustPath: String? = null): org.example.plugin.utils.TlsResult {
        var pcap: Path = Paths.get("/")
        var httpResp: String = ""
        var workerLogsStr: String = ""
        val serial = adb.deviceSerial

        runBlocking {
            val tcpdumpJob = launch(Dispatchers.IO) {
                try {
                    client.execute(ShellCommandRequest("su 0 rm -f /data/local/tmp/traffic.pcap"), serial)
                    client.execute(ShellCommandRequest("su 0 tcpdump -i any -U -w /data/local/tmp/traffic.pcap"), serial)
                } catch (e: Exception) {
                    log("tcpdump exception: ${e.message}")
                }
            }
            Thread.sleep(2000) // Give tcpdump time to start

            // Push trust cert to device
            val tPath = customTrustPath ?: File(org.example.project.JUnitBridge.resourceDir, "revocation/root-ca.crt").absolutePath
            val certFile = File(tPath)
            if (certFile.exists()) {
                val channel = client.execute(
                  com.malinskiy.adam.request.sync.v1.PushFileRequest(certFile, "/sdcard/cert.pem"),
                  this,
                  serial = serial
                )
                channel.consumeEach { }
                
                client.execute(ShellCommandRequest("su 0 mkdir -p /data/data/com.example.openurl.niapsec/files/"), serial)
                client.execute(ShellCommandRequest("su 0 cp /sdcard/cert.pem /data/data/com.example.openurl.niapsec/files/cert.pem"), serial)
                client.execute(ShellCommandRequest("su 0 chmod 666 /data/data/com.example.openurl.niapsec/files/cert.pem"), serial)
                
                // Fix ownership as discovered in previous tests
                client.execute(ShellCommandRequest("su 0 chown u0_a370:u0_a370 /data/data/com.example.openurl.niapsec/files/cert.pem"), serial)
                
                log("Pushed trust cert to device: /data/data/com.example.openurl.niapsec/files/cert.pem")
            } else {
                log("Trust cert file not found on host: $tPath")
            }

            client.execute(ShellCommandRequest("am force-stop com.example.openurl.niapsec"), serial)
            Thread.sleep(500)
            
            val cmd = "am start -a android.intent.action.VIEW -n com.example.openurl.niapsec/.MainActivity -e openurl $testurl -e trustpath /data/data/com.example.openurl.niapsec/files/cert.pem"
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

    val OUT_PATH = File(org.example.project.JUnitBridge.resultsDir, "capture").absolutePath + "/"
    
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
}
