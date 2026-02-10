package org.example.plugin

import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.plugin.utils.*
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File
import org.example.project.JUnitBridge

class FdpAcc1Test {

    @get:Rule
    val adbDeviceRule = AdbDeviceRule()

    // Assuming the APK is placed in "libs" directory in the project root
    //private val TEST_APK = File("libs/target-test-app-debug.apk")
    private val TEST_APK by lazy {
        File(JUnitBridge.resourceDir, "target-test-app-debug.apk")
    }
    private val TEST_PACKAGE = "org.example.target"
    @Test
    fun testUserAssets() = runBlocking {
        val client = adbDeviceRule.adb
        val serial = adbDeviceRule.deviceSerial
        logi(">>> [FDP_ACC1] Starting Test: Subset Access Control")

        if (!TEST_APK.exists()) {
            loge("Test APK not found at: ${TEST_APK.absolutePath}")
            return@runBlocking
        }

        // 1. Initial Cleanup & Install using common-utils
        AdamUtils.uninstallApk(client, serial, TEST_PACKAGE)
        AdamUtils.installApk(client, serial, TEST_APK)

        // 2. Prepare Data
        logi("Launching PrepareActivity...")
        client.execute(ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.PrepareActivity"), serial)
        delay(2000)

        // 3. Verify Access
        logi("Launching MainActivity...")
        client.execute(ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.MainActivity"), serial)

        // 4. Check Result
        val result1 = AdamUtils.waitForLogcat(client, serial, "FDP_ACC_1_TEST", "Test Result:")
        logi("Result 1: $result1")
        Assert.assertTrue("Failed initial check", result1?.contains("true/true/true/true") == true)

        // 5. Uninstall (removes app data)
        AdamUtils.uninstallApk(client, serial, TEST_PACKAGE)

        // 6. Reinstall & Verify Data Loss
        AdamUtils.installApk(client, serial, TEST_APK)
        delay(1000)

        logi("Launching MainActivity again...")
        client.execute(ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.MainActivity"), serial)

        // 7. Check Result (Expect data loss)
        val result2 = AdamUtils.waitForLogcat(client, serial, "FDP_ACC_1_TEST", "Test Result:")
        logi("Result 2: $result2")

        val expected = "false/false/true/false"
        if (result2?.contains(expected) == true) {
            logp("SUCCESS: Data access control verified.")
        } else {
            loge("FAILURE: Expected '$expected' but got '$result2'")
            Assert.fail("Access control check failed")
        }
    }
}