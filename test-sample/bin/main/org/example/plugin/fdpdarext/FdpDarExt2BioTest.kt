package org.example.plugin.fdpdarext

import org.junit.Ignore

@Ignore("not yet")
class FdpDarExt2BioTest {
    /*
    @Before
    fun setup() {
        runBlocking {
            logi("Setting up ${testName.methodName}...")

            // --- [前提条件チェック: 生体認証] ---

            // 1. 指紋認証サービスの登録状態をチェック
            // 典型的な出力: "hasEnrolledTemplates=true", "enrolled: true", "enrollments: 1"
            val fpCheck = AdamUtils.shellRequest("dumpsys fingerprint | grep -i -E 'enrolled|hasEnrolledTemplates'", adb)

            // 2. 顔認証サービスの登録状態をチェック (Android 10以降)
            val faceCheck = AdamUtils.shellRequest("dumpsys face | grep -i -E 'enrolled|hasEnrolledTemplates'", adb)

            // どちらか一方でも "true" や "1" (登録あり) を示していればOKとする
            val isBiometricEnrolled =
                fpCheck.contains("true", ignoreCase = true) ||
                fpCheck.matches(Regex(".*[1-9]+.*")) || // enrollments: 1 など
                faceCheck.contains("true", ignoreCase = true) ||
                faceCheck.matches(Regex(".*[1-9]+.*"))

            Assume.assumeTrue(
                "【スキップ】端末に生体認証（指紋または顔）が登録されていません。設定から登録して再実行してください。",
                isBiometricEnrolled
            )

            logi("Pre-conditions met: Biometric authentication is enrolled.")
            // ------------------------

            // 以降、既存のアプリインストール処理など...
            try { AdamUtils.uninstallApk(client, serial, TARGET_PACKAGE) } catch (e: Exception) {}
            // ...
        }
    }
     */
}