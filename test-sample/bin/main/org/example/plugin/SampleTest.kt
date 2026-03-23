package org.example.plugin

import org.example.plugin.utils.*
import org.example.project.JUnitBridge
import org.example.project.TestLogLevel
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore

@Ignore("Only for Operation Check")
class SampleTest {
    @get:Rule
    val adbDeviceRule = AdbDeviceRule()

    private val logger = JUnitBridge.logging
    @Test
    fun helloPluginTest() {

        logger?.invoke("--- [Plugin] Running Sample Test ---", TestLogLevel.INFO)

        val serial = adbDeviceRule.deviceSerial
        logger?.invoke("--- [Plugin] Target Device: $serial ---", TestLogLevel.DEBUG)

        loge("ERROR")
        logw("WARNING")
        logi("INFO")
    }
}