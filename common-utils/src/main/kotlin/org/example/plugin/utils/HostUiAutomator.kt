package org.example.plugin.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.example.project.adb.rules.AdbDeviceRule
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

data class UiBounds(val top: Int, val left: Int, val right: Int, val bottom: Int)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UiNode(
    val index: Int = 0,
    val text: String? = "",
    val resourceId: String? = "",
    val className: String? = "",
    val packageName: String? = "",
    val contentDescription: String? = "",
    val clickable: Boolean = false,
    val bounds: UiBounds? = null,
    val children: List<UiNode> = emptyList()
)

class BySelector {
    var text: String? = null
    var textContains: String? = null
    var res: String? = null
    var clazz: String? = null
    var pkg: String? = null

    fun text(text: String): BySelector { this.text = text; return this }
    fun textContains(text: String): BySelector { this.textContains = text; return this }
    fun res(res: String): BySelector { this.res = res; return this }
    fun clazz(clazz: String): BySelector { this.clazz = clazz; return this }
    fun pkg(pkg: String): BySelector { this.pkg = pkg; return this }

    fun matches(node: UiNode): Boolean {
        if (text != null && node.text != text) return false
        if (textContains != null && (node.text == null || !node.text.contains(textContains!!))) return false
        if (res != null && node.resourceId != res) return false
        if (clazz != null && node.className != clazz) return false
        if (pkg != null && node.packageName != pkg) return false
        return true
    }
}

object By {
    fun text(text: String) = BySelector().text(text)
    fun textContains(text: String) = BySelector().textContains(text)
    fun res(res: String) = BySelector().res(res)
    fun clazz(clazz: String) = BySelector().clazz(clazz)
    fun pkg(pkg: String) = BySelector().pkg(pkg)
}

class UiObject(val device: UiDevice, val node: UiNode) {
    fun click(): Boolean {
        val b = node.bounds ?: return false
        val cx = (b.left + b.right) / 2
        val cy = (b.top + b.bottom) / 2
        return device.tap(cx, cy)
    }
    
    fun getText(): String? = node.text
}

class UiDevice(val adb: AdbDeviceRule) {
    private val port = 11451
    private val mapper = jacksonObjectMapper()

    fun findObject(selector: BySelector): UiObject? {
        val root = getUiDump() ?: return null
        val matched = findNode(root, selector) ?: return null
        return UiObject(this, matched)
    }

    fun wait(selector: BySelector, timeoutMs: Long): UiObject? {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            val obj = findObject(selector)
            if (obj != null) return obj
            Thread.sleep(500)
        }
        return null
    }

    private fun getUiDump(): UiNode? {
        try {
            Socket("localhost", port).use { socket ->
                val writer = PrintWriter(socket.outputStream, true)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))

                val request = "{\"cmd\":\"get_ui_dump\"}"
                writer.println(request)

                val responseStr = reader.readLine() ?: return null
                val response: Map<String, Any> = mapper.readValue(responseStr)
                if (response["status"] != "ok") return null
                
                val outputStr = response["output"] as? String ?: return null
                return mapper.readValue<UiNode>(outputStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun tap(x: Int, y: Int): Boolean {
        try {
            Socket("localhost", port).use { socket ->
                val writer = PrintWriter(socket.outputStream, true)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))

                val request = "{\"cmd\":\"tap\",\"x\":$x,\"y\":$y}"
                writer.println(request)

                val responseStr = reader.readLine() ?: return false
                val response: Map<String, Any> = mapper.readValue(responseStr)
                return response["status"] == "ok"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun pressKeyCode(keycode: String): Boolean {
        try {
            Socket("localhost", port).use { socket ->
                val writer = PrintWriter(socket.outputStream, true)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))

                val request = "{\"cmd\":\"press_key\",\"keycode\":\"$keycode\"}"
                writer.println(request)

                val responseStr = reader.readLine() ?: return false
                val response: Map<String, Any> = mapper.readValue(responseStr)
                return response["status"] == "ok"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun pressBack() = pressKeyCode("KEYCODE_BACK")
    fun pressHome() = pressKeyCode("KEYCODE_HOME")

    fun inputText(text: String, pressEnter: Boolean = true): Boolean {
        try {
            Socket("localhost", port).use { socket ->
                val writer = PrintWriter(socket.outputStream, true)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))

                val request = "{\"cmd\":\"input_text\",\"text\":\"$text\",\"press_enter\":$pressEnter}"
                writer.println(request)

                val responseStr = reader.readLine() ?: return false
                val response: Map<String, Any> = mapper.readValue(responseStr)
                return response["status"] == "ok"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun findNode(node: UiNode, selector: BySelector): UiNode? {
        if (selector.matches(node)) return node
        for (child in node.children) {
            val found = findNode(child, selector)
            if (found != null) return found
        }
        return null
    }
}
