package org.example.plugin

import org.example.plugin.utils.logd
import org.example.plugin.utils.loge
import org.example.plugin.utils.logi
import org.example.plugin.utils.logp
import org.example.plugin.utils.logw
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