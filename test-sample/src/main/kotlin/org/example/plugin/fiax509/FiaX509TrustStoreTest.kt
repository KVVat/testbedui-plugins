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
import java.io.File
import com.malinskiy.adam.AndroidDebugBridgeClient
import kotlinx.coroutines.channels.consumeEach

/**
 * FIA_X509_EXT.1.6 Trust Store Management
 *
 * This test suite verifies that the TSF correctly manages trust stores
 * and isolates trusted CAs according to the configuration.
 */
@SFR("FIA_X509_EXT.1.6", """
The TSF shall manage trust stores used for certification path validation.
""", category="network")
class FiaX509TrustStoreTest {
    @Rule @JvmField val errs = ErrorCollector()
    @Rule @JvmField val adb = AdbDeviceRule()
    @Rule @JvmField val name = TestName()
    @Rule @JvmField val watcher = ADSRPTestWatcher(adb)

    private val a by lazy { TestAssertLogger(name) }
    private val client: AndroidDebugBridgeClient by lazy { adb.adb }
    private val hostName = "https://127.0.0.1:4449"
    
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

    private fun runTestWithTrustCert(trustCertName: String, expectedResult: String) {
        // Start server with cert signed by root-ca.crt
        startServer("ok_all")

        val serial = adb.deviceSerial
        val resourcesDir = File(org.example.project.JUnitBridge.resourceDir, "x509extensions")
        
        val trustCertFile = File(resourcesDir, "$trustCertName.crt")
        Assert.assertTrue("Trust cert file should exist: $trustCertName", trustCertFile.exists())

        runBlocking {
            // Clear logcat
            ProcessBuilder("adb", "-s", serial, "logcat", "-c").start().waitFor()

            // Push cert to device
            val channel = client.execute(
                com.malinskiy.adam.request.sync.v1.PushFileRequest(trustCertFile, "/sdcard/cert.pem"),
                this,
                serial = serial
            )
            channel.consumeEach { }

            // Copy to app storage
            client.execute(ShellCommandRequest("su 0 cp /sdcard/cert.pem /data/data/com.example.openurl.niapsec/files/cert.pem"), serial)
            client.execute(ShellCommandRequest("su 0 chmod 666 /data/data/com.example.openurl.niapsec/files/cert.pem"), serial)

            // Find app UID and chown
            val uidRes = client.execute(ShellCommandRequest("su 0 stat -c '%u' /data/data/com.example.openurl.niapsec/"), serial)
            val uid = uidRes.output.trim()
            if (uid.isNotEmpty() && uid.all { it.isDigit() }) {
                client.execute(ShellCommandRequest("su 0 chown $uid:$uid /data/data/com.example.openurl.niapsec/files/cert.pem"), serial)
                log("Chowned cert.pem to $uid")
            }

            // Setup adb reverse (using host command)
            ProcessBuilder("adb", "-s", serial, "reverse", "tcp:4449", "tcp:4449").start().waitFor()

            try {
                // Start app to connect to server
                client.execute(ShellCommandRequest("am force-stop com.example.openurl.niapsec"), serial)
                
                val cmd = "am start -a android.intent.action.VIEW -n com.example.openurl.niapsec/.MainActivity -e openurl $hostName -e trustpath /data/data/com.example.openurl.niapsec/files/cert.pem -e type http"
                client.execute(ShellCommandRequest(cmd), serial)

                // Wait for result
                val res = AdamUtils.waitLogcatLine(100, "worker@return", adb)
                Assert.assertNotNull("Should get a response", res)
                val text = res!!.text
                log("Found logcat line: $text")

                val marker = "worker@return:"
                val index = text.lastIndexOf(marker)
                val httpResp = if (index >= 0) text.substring(index + marker.length).trim() else text.trim()

                log("HTTP response: $httpResp")
                
                Assert.assertTrue("Response should contain $expectedResult", httpResp.contains(expectedResult))
            } finally {
                ProcessBuilder("adb", "-s", serial, "reverse", "--remove", "tcp:4449").start().waitFor()
            }
        }
    }

    @Test
    fun testTrustStoreIsolation() {
        log("Starting testTrustStoreIsolation...")
        // We expect failure (525 or similar) when trusting fake-ca but server uses root-ca
        runTestWithTrustCert("fake-ca", "525")
    }

    @Test
    fun testTrustStorePositive() {
        log("Starting testTrustStorePositive...")
        // We expect success (200) when trusting root-ca and server uses root-ca
        runTestWithTrustCert("root-ca", "200")
    }
}
