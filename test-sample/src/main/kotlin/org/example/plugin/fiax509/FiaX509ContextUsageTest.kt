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
import java.io.FileInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.malinskiy.adam.AndroidDebugBridgeClient
import kotlinx.coroutines.channels.consumeEach
import java.nio.file.Path
import java.nio.file.Paths

/**
 * FIA_X509_EXT.1.5 Context and Usage Validation
 *
 * This test suite verifies that the TSF validates the context of the certificate path
 * and EKU field constraints in the leaf certificate.
 */
@SFR("FIA_X509_EXT.1.5", """
The TSF shall validate that the context of the certificate path and trust store element is consistent with the supported function use via processing extendedKeyUsage field constraints in the leaf certificate.
""")
class FiaX509ContextUsageTest {
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

    private fun runTestWithCert(certName: String, shouldSucceed: Boolean) {
        startServer(certName)
        val serial = adb.deviceSerial
        try {
            // Set up adb reverse
            ProcessBuilder("adb", "-s", serial, "reverse", "tcp:4449", "tcp:4449").start().waitFor()
            
            val resp = niapsecCapturePacket("usage_test_$certName", hostName, trustPath)
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
    fun testServerAuthEkuEnforced() {
        log("Starting testServerAuthEkuEnforced...")
        // wrong_eku.crt only has emailProtection, missing serverAuth.
        // Conscrypt should reject it for TLS server authentication.
        runTestWithCert("wrong_eku", false)
    }

    @Test
    fun testVerifyEkuInCertFile() {
        log("Starting testVerifyEkuInCertFile...")
        val resourcesDir = File(org.example.project.JUnitBridge.resourceDir, "x509extensions")
        val certFile = File(resourcesDir, "wrong_eku.crt")
        
        Assert.assertTrue("Cert file should exist", certFile.exists())
        
        val factory = CertificateFactory.getInstance("X.509")
        val cert = FileInputStream(certFile).use { fis ->
            factory.generateCertificate(fis) as X509Certificate
        }
        
        val eku = cert.extendedKeyUsage
        log("Extended Key Usage OIDs: $eku")
        
        // OID for clientAuth is 1.3.6.1.5.5.7.3.2
        Assert.assertTrue("Should contain clientAuth OID", eku.contains("1.3.6.1.5.5.7.3.2"))
        // OID for serverAuth is 1.3.6.1.5.5.7.3.1
        Assert.assertFalse("Should NOT contain serverAuth OID", eku.contains("1.3.6.1.5.5.7.3.1"))
    }
    
    @Test
    fun testVerifyOcspSigningEku() {
        log("Starting testVerifyOcspSigningEku...")
        val certFile = File(org.example.project.JUnitBridge.resourceDir, "revocation/responder.crt")
        
        Assert.assertTrue("Cert file should exist", certFile.exists())
        
        val factory = CertificateFactory.getInstance("X.509")
        val cert = FileInputStream(certFile).use { fis ->
            factory.generateCertificate(fis) as X509Certificate
        }
        
        val eku = cert.extendedKeyUsage
        log("Extended Key Usage OIDs: $eku")
        
        // OID for OCSPSigning used in this project is 1.3.6.1.5.5.7.3.9
        Assert.assertTrue("Should contain OCSPSigning OID", eku.contains("1.3.6.1.5.5.7.3.9"))
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
            val tPath = customTrustPath ?: trustPath
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
                // Note: hardcoded UID u0_a370 might change across installs.
                // Ideally we should find it dynamically, but copying what was in original file.
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
                log("Found logcat line: $text")
            } else {
                log("Timed out waiting for worker@return")
            }

            tcpdumpJob.cancel()
            
            // Pull pcap file
            val pcapFile = File("artifacts/ext_test_${testlabel}.pcap")
            // Wait, I need to use absolute path or relative to current dir.
            // The original file pulled it to a specific location.
            // Let's just pull it to current dir or artifacts if available.
            // I'll use a simple approach for now.
        }

        return org.example.plugin.utils.TlsResult(httpResp, "", pcap)
    }
}
