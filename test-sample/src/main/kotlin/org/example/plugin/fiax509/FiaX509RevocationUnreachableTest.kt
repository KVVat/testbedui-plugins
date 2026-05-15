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
 * FIA_X509_EXT.2.2 Behavior when Revocation Status Cannot be Obtained
 *
 * This test suite verifies that the TSF allows the connection when
 * revocation status cannot be obtained.
 */
@SFR("FIA_X509_EXT.2.2", """
When the TSF cannot establish a connection to determine the revocation status of a certificate, the TSF shall allow the connection.
""")
class FiaX509RevocationUnreachableTest {
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

    private fun startServer(certFile: File, keyFile: File) {
        stopServer()
        
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

    @Test
    fun testRevocationUnreachableAllowsConnection() {
        val revocationDir = File(org.example.project.JUnitBridge.resourceDir, "revocation")
        val certFile = File(revocationDir, "server-valid.crt")
        val keyFile = File(revocationDir, "server-valid.key")
        val trustCertFile = File(revocationDir, "root-ca.crt")

        Assert.assertTrue("Cert file should exist", certFile.exists())
        Assert.assertTrue("Key file should exist", keyFile.exists())
        Assert.assertTrue("Trust cert file should exist", trustCertFile.exists())

        // Start server with cert that has OCSP URL (but responder is not running)
        startServer(certFile, keyFile)

        val serial = adb.deviceSerial

        runBlocking {
            // Clear logcat
            ProcessBuilder("adb", "-s", serial, "logcat", "-c").start().waitFor()

            // Push trust cert to device
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
            }

            // Setup adb reverse for the server
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
                
                // Connection should succeed (200) despite OCSP responder being unreachable
                Assert.assertTrue("Connection should succeed with 200", httpResp.contains("200"))
            } finally {
                ProcessBuilder("adb", "-s", serial, "reverse", "--remove", "tcp:4449").start().waitFor()
            }
        }
    }
}
