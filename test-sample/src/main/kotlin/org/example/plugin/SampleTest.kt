package org.example.plugin

import org.example.project.JUnitBridge
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.Rule
import org.junit.Test

class SampleTest {
    @get:Rule
    val adbDeviceRule = AdbDeviceRule()
    private val logger = JUnitBridge.logging

    @Test
    fun helloPluginTest() {
        logger?.invoke("--- [Plugin: giant-test-sample] 起動確認 ---")
        logger?.invoke("Target Device Serial: ${adbDeviceRule.deviceSerial}")
    }
}