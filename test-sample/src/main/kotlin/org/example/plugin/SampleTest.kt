package org.example.plugin

import com.example.plugin.utils.*
import org.example.project.JUnitBridge
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.Rule
import org.junit.Test

class SampleTest {
    @get:Rule
    val adbDeviceRule = AdbDeviceRule()

    @Test
    fun helloPluginTest() {
        logi("Start Test")
        logp("Passed Test")
        loge("Failed Test")
        logd("Debug Test")
        logw("Warning Test")
    }
}