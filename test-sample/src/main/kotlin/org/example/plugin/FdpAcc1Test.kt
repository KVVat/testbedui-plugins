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
    private val ATTACKER_APK by lazy {
        File(JUnitBridge.resourceDir, "assets-attacker-app.apk")
    }
    private val TEST_PACKAGE = "org.example.assets.target"
    private val ATTACKER_PACKAGE = "org.example.assets.attacker"
    /*
    @Before
    fun setUp()
    {

    }
    */
    @After
    fun teardown() {
        runBlocking {
            logi("Tearing down test... Uninstalling apps.")
            try {AdamUtils.uninstallApk(client, serial, TEST_PACKAGE)} catch (e: Exception) {}
            try {AdamUtils.uninstallApk(client, serial, ATTACKER_PACKAGE)} catch (e: Exception) {}
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


        if (!ATTACKER_APK.exists()) {
            loge("Attacker APK not found at: ${ATTACKER_APK.absolutePath}")
            return@runBlocking
        }

        // 8. Targetアプリで再度データを準備 (Attackerが盗めるかどうかの標的を作成)
        logi("Preparing data in target app for attacker test...")
        client.execute(
            ShellCommandRequest("am start -n $TEST_PACKAGE/$TEST_PACKAGE.PrepareActivity"),
            serial
        )
        delay(1000)
        // 9. Attackerアプリをインストールして起動
        AdamUtils.installApk(client, serial, ATTACKER_APK)
        delay(1000)

        logi("Launching Attacker MainActivity...")
        client.execute(
            ShellCommandRequest("am start -n $ATTACKER_PACKAGE/$ATTACKER_PACKAGE.MainActivity"),
            serial
        )

        // 10. Check Result (Attackerからのアクセス: Media以外は失敗するはず)
        val result3 = AdamUtils.waitLogcatLine(100, "FDP_ACC_1_TEST:RESULT", adbDeviceRule)
        logi("Result 3 (Attacker): $result3")

        val expectedAttacker = "false/false/true/false"
        if (result3?.text?.contains(expectedAttacker) == true) {
            logp("SUCCESS: Attacker access control verified (Sandbox is working).")
        } else {
            loge("FAILURE: Attacker access control. Expected '$expectedAttacker' but got '$result3'")
            Assert.fail("Attacker access check failed")
        }

    }
}