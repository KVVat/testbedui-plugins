package org.example.plugin.fdpdarext

import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.example.plugin.utils.ADSRPTestWatcher
import org.example.plugin.utils.AdamUtils
import org.example.plugin.utils.FileResource
import org.example.plugin.utils.SFR
import org.example.plugin.utils.TestAssertLogger
import org.example.plugin.utils.logi
import org.example.project.adb.rules.AdbDeviceRule
import org.hamcrest.CoreMatchers.anyOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.rules.TestName
import org.junit.rules.TestWatcher

class FdpDarExt2Test {

    private val TARGET_PACKAGE = "com.example.encryption"
    private val TEST_MODULE = "encryption-debug.apk" // 実際のファイル名に合わせてください

    @get:Rule
    val adb = AdbDeviceRule()
    private val client get() = adb.adb
    private val serial get() = adb.deviceSerial

    @get:Rule
    val watcher: TestWatcher by lazy { ADSRPTestWatcher(adb) }

    @get:Rule
    val errs: ErrorCollector = ErrorCollector()

    @get:Rule
    val testName: TestName = TestName()

    private val assert: TestAssertLogger by lazy { TestAssertLogger(testName) }
    private val TEST_APK by lazy { FileResource(TEST_MODULE) }

    // テスト用のダミーPINコード
    private val DEVICE_PIN = "1234"

    // ログキャプチャ用のタグ (アプリ側の出力に合わせて変更してください)
    private val LOG_TAG = "FDP_DAR_EXT_2_TEST"

    @Before
    fun setup() {
        runBlocking {
            logi("Setting up ${testName.methodName}...")

//            // --- [1. PINロックの強制設定] ---
//            logi("Ensuring device is secured with PIN '$DEVICE_PIN'...")
//            // 一旦既存のPINをクリア（もしあれば）して、確実に1234を設定する
//            // ※元のPINが不明な場合はクリアできないため、テスト専用端末であることを前提とします
//            AdamUtils.shellRequest("locksettings clear --old 1234", adb)
//            val setPinResult = AdamUtils.shellRequest("locksettings set-pin $DEVICE_PIN", adb)
//
//            // 設定が成功したか（または既に設定済みか）を確認
//            /*    "Prerequisite doesn't met: Failed to set PIN via locksettings. Result: $setPinResult. Please clear lock settings for testing",
//                setPinResult.output.contains("Pin set to") || setPinResult.output.contains("already")
//            )*/
//            // --- [2. 画面を点灯し、アンロック状態にする] ---
//            logi("Waking up and unlocking device...")
//            AdamUtils.shellRequest("input keyevent KEYCODE_WAKEUP", adb)
//            Thread.sleep(1000)
//            // キーガードをディスミスし、念のためPINを入力して確実にホーム画面へ
//            AdamUtils.shellRequest("wm dismiss-keyguard", adb)
//            AdamUtils.shellRequest("input text $DEVICE_PIN", adb)
//            AdamUtils.shellRequest("input keyevent KEYCODE_ENTER", adb)
//            Thread.sleep(1000)
//
//            // 既存のアプリをアンインストールし、クリーンな状態でインストール
//            try {
//                AdamUtils.uninstallApk(client, serial, TARGET_PACKAGE)
//            } catch (e: Exception) {
//                //Assume.assumeNoException("Failed to uninstall existing app: ${e.toString()}");
//            }
//            val ret = AdamUtils.installApk(client, serial, TEST_APK)
//            logi("APK Installation result: $ret")
//            Assume.assumeTrue("APK Installation failed", !ret.startsWith("Success"))
        }
    }



    /**
     * FDP_DAR_EXT.2.2: Asymmetric Key Encryption While Locked
     * デバイスがロックされている状態でも、「公開鍵」を用いて機密データを暗号化して保存できることを検証する。
     */
    @Test
    @SFR("FDP_DAR_EXT.2.2", description = "")
    fun testAsymmetricEncryptionWhileLocked() {
        runBlocking {
            logi("Starting FDP_DAR_EXT.2.2: Public key encryption test")

            // 1. デバイスをスリープ状態（ロック）にする
            logi("Step 1: Locking device...")
            AdamUtils.shellRequest("input keyevent KEYCODE_SLEEP", adb)
            Thread.sleep(2000)

            // 2. アプリをバックグラウンド等でキックし、「公開鍵」での暗号化を指示する
            logi("Step 2: Triggering public key encryption while locked...")
            async {
                AdamUtils.shellRequest(
                    "am start -n $TARGET_PACKAGE/.MainActivity " +
                            "-e usePublicKey true " + // 公開鍵を使うフラグ(仮)
                            "-e encryptData true",
                    adb
                )
            }

            // 3. 例外（UserNotAuthenticatedException等）が発生せず、暗号化が成功したログを探す
            var result = AdamUtils.waitLogcatLine(50, LOG_TAG, adb)
            if (result == null) {
                result = AdamUtils.LogcatResult("", "<null>")
            }

            errs.checkThat(
                assert.msg("FDP_DAR_EXT.2.2: TSF shall successfully encrypt data using a public key while the device is locked."),
                result.text,
                containsString("Encrypted information:") // アプリの成功ログに合わせてください
            )
        }
    }

    /**
     * FDP_DAR_EXT.2.3: Key Release via Device Credential (PIN)
     * UDR(UnlockDeviceRequired)が設定された秘密鍵が、ロック中はブロックされ、
     * パスコード認証後に解放されることを検証する。
     */
    @Test
    @SFR("FDP_DAR_EXT.2.3","")
    fun testKeyReleaseOnPinUnlock() {
        runBlocking {
            logi("Starting FDP_DAR_EXT.2.3: UDR Private Key release test via PIN")

            // 1. デバイスをスリープ状態（ロック）にする
            logi("Step 1: Locking device...")
            AdamUtils.shellRequest("input keyevent KEYCODE_SLEEP", adb)
            Thread.sleep(2000)

            // 2. ロック状態で、アプリに「秘密鍵（UDRあり）」での復号（または使用）を指示する
            logi("Step 2: Attempting to use UDR private key while locked...")
            async {
                AdamUtils.shellRequest(
                    "am start -n $TARGET_PACKAGE/.MainActivity " +
                            "-e unlockDeviceRequired true " +
                            "-e usePrivateKey true", // 秘密鍵を使うフラグ(仮)
                    adb
                )
            }

            // 3. ロック中は UserNotAuthenticatedException 等でアクセスが拒否されることを確認
            var logcatLocked = AdamUtils.waitLogcatLine(50, LOG_TAG, adb)
            if (logcatLocked == null) {
                logcatLocked = AdamUtils.LogcatResult("", "<null>")
            }

            errs.checkThat(
                assert.msg("FDP_DAR_EXT.2.3: TSF shall block access to the private key when the device is locked (UDR enforced)."),
                logcatLocked.text,
                anyOf(
                    containsString("Auth:Failed"),
                    containsString("UserNotAuthenticatedException")
                )
            )

            // 4. UIAutomator または Shell コマンドで画面を点灯し、PINを入力してロックを解除する
            logi("Step 3: Unlocking device using PIN ($DEVICE_PIN)...")
            AdamUtils.shellRequest("input keyevent KEYCODE_WAKEUP", adb)
            Thread.sleep(1000)
            AdamUtils.shellRequest("input swipe 500 1500 500 500", adb)
            Thread.sleep(1000)
            AdamUtils.shellRequest("input text $DEVICE_PIN", adb)
            AdamUtils.shellRequest("input keyevent KEYCODE_ENTER", adb)
            Thread.sleep(2000)

            // 5. アンロック状態でもう一度「秘密鍵」の使用を指示する
            logi("Step 4: Attempting to use UDR private key after unlock...")
            async {
                AdamUtils.shellRequest(
                    "am start -n $TARGET_PACKAGE/.MainActivity " +
                            "-e unlockDeviceRequired true " +
                            "-e usePrivateKey true",
                    adb
                )
            }

            // 6. 今度は例外が発生せず、処理が成功したログを探す
            var logcatUnlocked = AdamUtils.waitLogcatLine(50, LOG_TAG, adb)
            if (logcatUnlocked == null) {
                logcatUnlocked = AdamUtils.LogcatResult("", "<null>")
            }

            errs.checkThat(
                assert.msg("FDP_DAR_EXT.2.3: TSF shall release the private key after successful password/PIN authentication."),
                logcatUnlocked.text,
                anyOf(
                    containsString("Auth:Success"),
                    containsString("Decrypted information:")
                )
            )
        }
    }

    /*
    @After
    fun teardown() {
        runBlocking {
            logi("Tearing down ${testName.methodName}...")
            try { AdamUtils.uninstallApk(client, serial, TARGET_PACKAGE) } catch (e: Exception) {}

            // 画面ロック状態を解除しておく
            AdamUtils.shellRequest("input keyevent KEYCODE_WAKEUP", adb)
            AdamUtils.shellRequest("wm dismiss-keyguard", adb)
            AdamUtils.shellRequest("input text $DEVICE_PIN", adb)
            AdamUtils.shellRequest("input keyevent KEYCODE_ENTER", adb)

            // --- [3. テスト終了時にPINをクリア（元の状態に戻す）] ---
            logi("Clearing test PIN...")
            AdamUtils.shellRequest("locksettings clear --old $DEVICE_PIN", adb)
        }
    }*/
}