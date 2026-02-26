package org.example.plugin

import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.loge
import org.example.plugin.utils.logi
import org.example.plugin.utils.logp
import org.example.project.JUnitBridge
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File

class FdpAcc1Test {

    @get:Rule
    val adbDeviceRule = AdbDeviceRule()
    val client get() = adbDeviceRule.adb
    val serial get() = adbDeviceRule.deviceSerial

    // Assuming the APK is placed in "libs" directory in the project root
    private val TEST_APK by lazy {
        File(JUnitBridge.resourceDir, "assets-target-app.apk")
    }
    private val TEST_PACKAGE = "org.example.assets.target"
    /*
    @Before
    fun setUp()
    {

    }
    */
    @After
    fun teardown() {
        runBlocking {
            AdamUtils.uninstallApk(client, serial, TEST_PACKAGE)
        }
    }

    @Test
    fun testUserAssets() = runBlocking {

        logi("Launching testUserAssets...")
        delay(500)
        if (!TEST_APK.exists()) {
            loge("Test APK not found at: ${TEST_APK.absolutePath}")
            return@runBlocking
        }

        // 1. Initial Cleanup & Install using common-utils
        AdamUtils.uninstallApk(client, serial, TEST_PACKAGE)
        AdamUtils.installApk(client, serial, TEST_APK)

        // 2. Prepare Data
        logi("Launching PrepareActivity of target app...")
        client.execute(
            ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.PrepareActivity"),
            serial
        )
        delay(1000)

        // 3. Verify Access
        logi("Launching MainActivity of target app...")
        client.execute(
            ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.MainActivity"),
            serial
        )

        // 4. Check Result
        val result1 = AdamUtils.waitLogcatLine(100, "FDP_ACC_1_TEST:RESULT", adbDeviceRule)
        logi("Result 1: $result1")
        var expected = "true/true/true/true"
        if (result1?.text?.contains(expected) == true) {
            logp("SUCCESS: Data access verified.")
        } else {
            loge("FAILURE: Data access. Expected '$expected' but got '$result1'")
            Assert.fail("Access control check failed")
        }

        Assert.assertTrue(
            "Failed initial check",
            result1?.text?.contains("true/true/true/true") == true
        )

        // 5. Uninstall (removes app data)
        AdamUtils.uninstallApk(client, serial, TEST_PACKAGE)

        // 6. Reinstall & Verify Data Loss
        AdamUtils.installApk(client, serial, TEST_APK)
        delay(1000)

        logi("Launching MainActivity again...")
        client.execute(
            ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.MainActivity"),
            serial
        )

        // 7. Check Result (Expect data loss)
        val result2 = AdamUtils.waitLogcatLine(100, "FDP_ACC_1_TEST:RESULT", adbDeviceRule)
        logi("Result 2: $result2")

        expected = "false/false/true/false"
        if (result2?.text?.contains(expected) == true) {
            logp("SUCCESS: Data access control verified.")
        } else {
            loge("FAILURE: Data access control. Expected '$expected' but got '$result2'")
            Assert.fail("Access control check failed")
        }
    }
}