package org.example.plugin
こ
import org.example.plugin.utils.*
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class LongRunningTest {
    @get:Rule
    val adbDeviceRule = AdbDeviceRule()

    @Test
    fun longRunningLogTest() {
        val totalSeconds = 60 // 120s is a bit long for repeated tests
        val intervalSeconds = 5L

        logi("--- [Plugin] Starting Long Running Test (Duration: ${totalSeconds}s) ---")

        for (i in 0..(totalSeconds / intervalSeconds)) {
            val elapsed = i * intervalSeconds
            logi("--- [Plugin] Elapsed: ${elapsed}s / ${totalSeconds}s ---")
            
            if (elapsed % (intervalSeconds * 3) == 0L) {
                logd("--- [Plugin] Debug message at ${elapsed}s ---")
            }
            if (elapsed % (intervalSeconds * 5) == 0L && elapsed > 0) {
                logw("--- [Plugin] Warning message at ${elapsed}s ---")
            }

            if (elapsed < totalSeconds) {
                TimeUnit.SECONDS.sleep(intervalSeconds)
            }
        }

        logp("--- [Plugin] Long Running Test Completed Successfully ---")
    }

    @Test
    fun errorDuringRunningTest() {
        logi("--- [Plugin] Starting Test that will fail ---")
        TimeUnit.SECONDS.sleep(10)
        logw("--- [Plugin] Something is going wrong... ---")
        TimeUnit.SECONDS.sleep(5)
        loge("--- [Plugin] Critical error occurred! ---")
        throw IllegalStateException("Simulated test failure")
    }
}
