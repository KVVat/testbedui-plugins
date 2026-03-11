package org.example.plugin.ftpitc

import com.malinskiy.adam.request.shell.v2.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.SFR
import org.example.plugin.utils.loge
import org.example.plugin.utils.logi
import org.example.plugin.utils.logp
import org.example.project.JUnitBridge
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Trusted Channel Communication - The TSF shall be capable of using TLS to provide a 
 * trusted communication channel between itself and another trusted IT product.
 */
@SFR(
    title = "FTP_ITC_EXT.1",
    description = "Verify that cleartext HTTP communication is correctly blocked by the OS Network Security Policy."
)
class FtpItcExt1HttpTest {

    @get:Rule
    val adbDeviceRule = AdbDeviceRule()
    val client get() = adbDeviceRule.adb
    val serial get() = adbDeviceRule.deviceSerial

    private val TEST_APK by lazy {
        File(JUnitBridge.resourceDir, "openurl-debug.apk")
    }
    private val TEST_PACKAGE = "com.example.openurl"

    @Before
    fun setUp() {
        runBlocking {
            if (!TEST_APK.exists()) {
                loge("Test APK not found at: ${TEST_APK.absolutePath}")
                Assert.fail("Test APK not found")
            }
            AdamUtils.installApk(client, serial, TEST_APK, reinstall = true)
        }
    }

    @After
    fun teardown() {
        runBlocking {
            try {
                AdamUtils.uninstallApk(client, serial, TEST_PACKAGE)
            } catch (e: Exception) {}
        }
    }

    @Test
    fun testCleartextBlocked() = runBlocking {
        logi("Starting testCleartextBlocked (FTP_ITC_EXT.1)...")

        // Clear logcat
        client.execute(ShellCommandRequest("logcat -c"), serial)

        val httpUrl = "http://example.com"
        logi("Attempting HTTP connection to $httpUrl using $TEST_PACKAGE")

        // Trigger MainActivity via am start
        val command = "am start -W -n $TEST_PACKAGE/$TEST_PACKAGE.MainActivity --es openurl \"$httpUrl\" --es type \"http\""
        client.execute(ShellCommandRequest(command), serial)

        logi("Waiting for NetworkWorker to be blocked by OS policy...")
        kotlinx.coroutines.delay(10000)

        // Check logcat for the expected error message
        // Android message varies slightly between versions:
        // - "CLEARTEXT communication to ... not permitted"
        // - "Cleartext HTTP traffic to ... not permitted"
        val logcatResult = client.execute(ShellCommandRequest("logcat -d worker@:V *:E"), serial)
        val logs = String(logcatResult.stdout)
        
        val isBlocked = logs.contains("not permitted") && (logs.contains("Cleartext") || logs.contains("CLEARTEXT"))
        
        if (isBlocked) {
            logp("SUCCESS: Cleartext communication was correctly blocked by the OS Network Security Policy.")
            logi("Detected log: ${logs.lineSequence().find { it.contains("not permitted") }}")
        } else {
            loge("FAILURE: OS did not block the cleartext communication, or the error was not logged.")
            logi("Recent Error Logs:\n$logs")
            Assert.fail("Cleartext communication was not blocked")
        }
    }
}
