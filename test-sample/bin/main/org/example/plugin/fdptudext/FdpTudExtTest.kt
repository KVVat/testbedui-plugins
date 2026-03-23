package org.example.plugin.fdptudext

import org.example.project.adb.rules.AdbDeviceRule
import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import com.malinskiy.adam.request.pkg.UninstallRemotePackageRequest
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.StringStartsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher
import java.io.File
import java.nio.file.Paths
import com.malinskiy.adam.AndroidDebugBridgeClient
import org.example.plugin.utils.*
import org.example.project.JUnitBridge
import org.junit.Assert

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

@SFR("FDP_TUD_EXT.3,5", """
FDP_TUD_EXT.3 Application Signing
Description:
The TSF shall verify the digital signature of all applications prior to installation or update. This test confirms that the TSF mandates a valid signature to ensure the integrity and authenticity of the application package (APK), and that it strictly rejects any unsigned or improperly signed applications.    

FPT_TUD_EXT.5 Application Verification
Description:
The TSF shall provide a mechanism to verify that an application update is digitally signed by the same entity as the version currently installed (Same-sign-on). This test validates that the TSF rejects updates with mismatched signatures and prevents "downgrade" attacks by blocking the installation of application versions with a lower version code than the one currently present on the device.  
""")
class FdpTudExtTest {
    @get:Rule
    val adbDeviceRule = AdbDeviceRule()
    @get:Rule
    val testName = TestName()

    private val client get() = adbDeviceRule.adb
    private val serial get() = adbDeviceRule.deviceSerial

    private val logger by lazy { TestAssertLogger(testName) }

    // テスト対象パッケージ名
    private val TARGET_PACKAGE = "com.example.appupdate"

    // リソースディレクトリからテスト用APKを取得
    private val APK_V1 by lazy { FileResource( "appupdate-v1.apk") }
    private val APK_V2 by lazy { FileResource("appupdate-v2.apk") }
    private val APK_UNSIGNED by lazy { FileResource("appupdate-unsigned.apk") }
    private val APK_MISMATCHED by lazy { FileResource( "appupdate-mismatched.apk") }

    @Before
    fun setUp() = runBlocking {
        logi("Setting up ${testName.methodName}...")
        // 既存のパッケージをクリーンアップ
        try { AdamUtils.uninstallApk(client, serial, TARGET_PACKAGE) } catch (e: Exception) {}
    }
    @Test
    fun testUserAssets() = runBlocking {
        //logi(logger.msg("Starting FPT_TUD_EXT.3: Unsigned APK rejection test")!!)

    }
    /**
     * FPT_TUD_EXT.3: verify that unsigned APKs are rejected.
     */
    @Test
    fun testUnsignedApkRejection() = runBlocking {
        logi(logger.msg("Starting FPT_TUD_EXT.3: Unsigned APK rejection test")!!)

        try {
            AdamUtils.installApk(client, serial, APK_UNSIGNED)
            // ここに到達したら失敗（インストールできてしまった）
            loge(logger.msg("FAILURE: Unsigned APK was unexpectedly installed.")!!)
            Assert.fail("TSF allowed installation of an unsigned APK.")
        } catch (e: Exception) {
            // Success: インストールが拒否された
            logp(logger.msg("SUCCESS: Unsigned APK was correctly rejected by TSF.")!!)
            logi("Error details: ${e.message}")
        }

    }

    /**
     * FPT_TUD_EXT.5 (Same-sign-on): verify update with mismatched signature is rejected.
     */
    @Test
    fun testMismatchedSignatureUpdateRejection() = runBlocking {
        logi(logger.msg("Starting FPT_TUD_EXT.5: Same-sign-on test")!!)

        // 1. 正当な鍵で署名された v1 をインストール
        logi("Step 1: Installing original signed APK...")
        AdamUtils.installApk(client, serial, APK_V1)

        // 2. 異なる鍵で署名された APK でアップデートを試みる
        logi("Step 2: Attempting update with mismatched signature APK...")
        try {
            // reinstall=true でアップデートを試行
            AdamUtils.installApk(client, serial, APK_MISMATCHED, reinstall = true)

            loge(logger.msg("FAILURE: Update with mismatched signature was unexpectedly allowed.")!!)
            Assert.fail("TSF allowed update with a different signature (Same-sign-on violation).")
        } catch (e: Exception) {
            logp(logger.msg("SUCCESS: Mismatched signature update was correctly rejected.")!!)
            logi("Error details: ${e.message}")
        }
    }

    /**
     * FPT_TUD_EXT.5 (Rollback Protection): verify version downgrade is rejected.
     */
    @Test
    fun testVersionDowngradeRejection() = runBlocking {
        logi(logger.msg("Starting FPT_TUD_EXT.5: Rollback protection test")!!)

        // 1. バージョンの高い APK (v2) をインストール
        logi("Step 1: Installing higher version APK (v2)...")
        AdamUtils.installApk(client, serial, APK_V2)

        // 2. バージョンの低い APK (v1) でアップデート（ダウングレード）を試みる
        logi("Step 2: Attempting downgrade to v1...")
        try {
            AdamUtils.installApk(client, serial, APK_V1, reinstall = true)

            loge(logger.msg("FAILURE: Version downgrade was unexpectedly allowed.")!!)
            Assert.fail("TSF allowed installation of a lower version over a higher one.")
        } catch (e: Exception) {
            logp(logger.msg("SUCCESS: Version downgrade was correctly rejected by TSF.")!!)
            logi("Error details: ${e.message}")
        }
    }

    @After
    fun tearDown() = runBlocking {
        logi("Tearing down ${testName.methodName}...")
        try { AdamUtils.uninstallApk(client, serial, TARGET_PACKAGE) } catch (e: Exception) {}
    }
}