package org.example.plugin.utils// testbedui-plugins/common-utils/src/main/kotlin/.../LogExtensions.kt

import org.example.project.JUnitBridge
import org.example.project.TestLogLevel

fun logd(message: String) = JUnitBridge.logging?.invoke(message, TestLogLevel.DEBUG)
fun logi(message: String) = JUnitBridge.logging?.invoke(message, TestLogLevel.INFO)
fun logp(message: String) = JUnitBridge.logging?.invoke(message, TestLogLevel.PASS)
fun logw(message: String) = JUnitBridge.logging?.invoke(message, TestLogLevel.WARN)
fun loge(message: String) = JUnitBridge.logging?.invoke(message, TestLogLevel.ERROR)

