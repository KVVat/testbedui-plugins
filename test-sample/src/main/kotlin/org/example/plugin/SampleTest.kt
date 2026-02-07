package org.example.plugin

import org.example.plugin.utils.*
import org.example.project.JUnitBridge
import org.example.project.TestLogLevel
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.Rule
import org.junit.Test

class SampleTest {
    @get:Rule
    val adbDeviceRule = AdbDeviceRule()

    private val logger = JUnitBridge.logging
    @Test
    fun helloPluginTest() {

        logger?.invoke("--- [Plugin] 巨大なテストシナリオを開始します ---", TestLogLevel.INFO)

        val serial = adbDeviceRule.deviceSerial
        logger?.invoke("--- [Plugin] 対象デバイス: $serial ---", TestLogLevel.DEBUG)

        loge("ERROR")
    }
}