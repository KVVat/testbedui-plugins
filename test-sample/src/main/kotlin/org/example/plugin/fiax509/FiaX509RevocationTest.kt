package org.example.plugin.fiax509

import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.TestAssertLogger
import org.example.plugin.utils.SFR
import org.example.plugin.utils.SFRCheckList
import org.example.plugin.utils.log
import org.example.plugin.utils.*
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
 * FIA_X509_EXT.1.3 & 1.4 Revocation Status Validation (OCSP/CRL)
 *
 * This test suite verifies the behavior of the NIAPSEC library and Conscrypt
 * regarding certificate revocation checking using OCSP and CRL.
 * It uses the dedicated openurl-niapsec app for testing.
 */
@SFR("FIA_X509_EXT", """
The TSF shall validate revocation status of the certificate using OCSP or CRL.
""", category = "network")
class FiaX509RevocationTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupCheckList() {
            SFRCheckList.register("FIA_X509_EXT.1.3/OCSP", "Verify revocation status validation via OCSP")
            SFRCheckList.register("FIA_X509_EXT.1.3/CRL", "Verify revocation status validation via CRL")
            SFRCheckList.register("FIA_X509_EXT.1.4", "Verify obtaining revocation status information")
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

    private val TEST_MODULE = "openurl-niapsec-debug.apk"

    @Before
    fun setup() {
        // TODO: Install openurl-niapsec-debug.apk if needed
    }

    @After
    fun teardown() {
        // Cleanup
    }

    @Test
    fun testOcspValid() {
        log("Starting testOcspValid with custom server...")
        val hostName = "https://127.0.0.1:4443"
        val resp = niapsecCapturePacket("custom_valid", hostName)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be 200"), httpret.startsWith("200"), IsEqual(true))
        
        SFRCheckList.pass("FIA_X509_EXT.1.3/OCSP")
    }

    @Test
    fun testOcspRevoked() {
        log("Starting testOcspRevoked with custom server (revoked)...")
        val hostName = "https://127.0.0.1:4444"
        val resp = niapsecCapturePacket("custom_revoked", hostName)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        // We expect failure (not 200)
        errs.checkThat(a.msg("HTTP response should not be 200 (revoked)"), httpret.startsWith("200"), IsEqual(false))
    }

    @Test
    fun testOcspCnsaValid() {
        log("Starting testOcspCnsaValid with CNSA server...")
        val hostName = "https://127.0.0.1:4445"
        val trustPath = File(org.example.project.JUnitBridge.resourceDir, "revocation/cnsa/root-ca.crt").absolutePath
        val resp = niapsecCapturePacket("custom_cnsa_valid", hostName, trustPath)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be 200"), httpret.startsWith("200"), IsEqual(true))
    }

    @Test
    fun testOcspEcdsaValid() {
        log("Starting testOcspEcdsaValid with ECDSA server...")
        val hostName = "https://127.0.0.1:4446"
        val trustPath = File(org.example.project.JUnitBridge.resourceDir, "revocation/ecdsa/root-ca.crt").absolutePath
        val resp = niapsecCapturePacket("custom_ecdsa_valid", hostName, trustPath)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be 200"), httpret.startsWith("200"), IsEqual(true))
    }

    @Test
    fun testOcspSha512Valid() {
        log("Starting testOcspSha512Valid with SHA512 server...")
        val hostName = "https://127.0.0.1:4447"
        val trustPath = File(org.example.project.JUnitBridge.resourceDir, "revocation/sha512/root-ca.crt").absolutePath
        val resp = niapsecCapturePacket("custom_sha512_valid", hostName, trustPath)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be 200"), httpret.startsWith("200"), IsEqual(true))
    }

    @Test
    fun testOcspSha384OldCaValid() {
        log("Starting testOcspSha384OldCaValid with SHA384 (Old CA) server...")
        val hostName = "https://127.0.0.1:4448"
        val trustPath = File(org.example.project.JUnitBridge.resourceDir, "revocation/root-ca.crt").absolutePath
        val resp = niapsecCapturePacket("custom_sha384_old_ca_valid", hostName, trustPath)
        val httpret = resp.httpResponse
        log("HTTP response: $httpret")

        errs.checkThat(a.msg("HTTP response should be 200"), httpret.startsWith("200"), IsEqual(true))
    }

    @Test
    fun testCrlValid() {
        log("Starting testCrlValid...")
        // TODO: Connect to a server with a valid certificate and working CRL
    }

    @Test
    fun testCrlRevoked() {
        log("Starting testCrlRevoked...")
        // TODO: Connect to a server with a certificate revoked via CRL.
    }

    private fun niapsecCapturePacket(testlabel:String, testurl:String, customTrustPath: String? = null): org.example.plugin.utils.TlsResult {
        var pcap: Path = Paths.get("/")
        var httpResp: String = ""
        var workerLogsStr: String = ""
        val serial = adb.deviceSerial

        runBlocking {
//            val browserApk = File(org.example.project.JUnitBridge.resourceDir, "openurl-niapsec-debug.apk")
//            val ret = AdamUtils.installApk(client, serial, browserApk, true)
//            Assert.assertTrue("Failed to install openurl-niapsec app: ${ret}", ret.startsWith("Success"))

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
            val trustPath = customTrustPath ?: File(org.example.project.JUnitBridge.resourceDir, "revocation/root-ca.crt").absolutePath
            val certFile = File(trustPath)
            if (certFile.exists()) {
                val channel = client.execute(
                  com.malinskiy.adam.request.sync.v1.PushFileRequest(certFile, "/sdcard/cert.pem"),
                  this,
                  serial = serial
                )
                for (progress in channel) {}
                
                client.execute(ShellCommandRequest("su 0 mkdir -p /data/data/com.example.openurl.niapsec/files/"), serial)
                client.execute(ShellCommandRequest("su 0 cp /sdcard/cert.pem /data/data/com.example.openurl.niapsec/files/cert.pem"), serial)
                client.execute(ShellCommandRequest("su 0 chmod 666 /data/data/com.example.openurl.niapsec/files/cert.pem"), serial)
                log("Pushed trust cert to device: /data/data/com.example.openurl.niapsec/files/cert.pem")
            } else {
                log("Trust cert file not found on host: $trustPath")
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
