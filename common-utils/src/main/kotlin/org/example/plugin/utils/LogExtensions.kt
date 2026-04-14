package org.example.plugin.utils

import org.example.project.JUnitBridge
import org.example.project.TestLogLevel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object LocalLog {
    val logs = mutableListOf<String>()

    fun add(level: String?, message: String) {
        if (level != null) {
            logs.add("[$level] $message")
        } else {
            logs.add(message)
        }
    }
    fun clear() {
        logs.clear()
    }
    fun getAsString(): String {
        return logs.joinToString("\n")
    }
}

object SFRCheckList {
    val items = mutableMapOf<String, SFRItem>()
    
    data class SFRItem(val description: String, var passed: Boolean = false)
    
    fun register(sfr: String, desc: String) {
        items[sfr] = SFRItem(desc)
    }
    
    fun pass(sfr: String) {
        items[sfr]?.passed = true
    }
    
    fun getSummaryString(): String {
        return items.entries.joinToString(" | ") { (sfr, item) ->
            "$sfr: ${if (item.passed) "PASS" else "FAIL"} (${item.description})"
        }
    }
}

fun log(message: String) { LocalLog.add(null, message); JUnitBridge.logging?.invoke(message, TestLogLevel.INFO)}
fun logd(message: String) { LocalLog.add("DEBUG", message); JUnitBridge.logging?.invoke(message, TestLogLevel.DEBUG)}
fun logi(message: String) { LocalLog.add("INFO", message); JUnitBridge.logging?.invoke(message, TestLogLevel.INFO)}
fun logp(message: String) { LocalLog.add("PASS", message); JUnitBridge.logging?.invoke(message, TestLogLevel.PASS)}
fun logw(message: String) { LocalLog.add("WARN", message); JUnitBridge.logging?.invoke(message, TestLogLevel.WARN)}
fun loge(message: String) { LocalLog.add("ERROR", message); JUnitBridge.logging?.invoke(message, TestLogLevel.ERROR)}

