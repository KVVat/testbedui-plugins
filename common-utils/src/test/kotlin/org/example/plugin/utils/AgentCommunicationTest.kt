package org.example.plugin.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.example.project.adb.rules.AdbDeviceRule
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class AgentCommunicationTest {

    @get:Rule
    val adb = AdbDeviceRule()

    @Test
    fun testPingAgent() {
        runBlocking {
            println("Testing ping to agent...")
            try {
                Socket("localhost", 11451).use { socket ->
                    val writer = PrintWriter(socket.outputStream, true)
                    val reader = BufferedReader(InputStreamReader(socket.inputStream))

                    val request = "{\"cmd\":\"ping\"}"
                    writer.println(request)

                    val responseStr = reader.readLine()
                    println("Response: $responseStr")
                    
                    assert(responseStr != null && responseStr.contains("pong"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                assert(false) { "Failed to connect to agent: ${e.message}" }
            }
        }
    }

    @Test
    fun testGetUiDump() {
        runBlocking {
            println("Testing get_ui_dump from agent...")
            try {
                Socket("localhost", 11451).use { socket ->
                    val writer = PrintWriter(socket.outputStream, true)
                    val reader = BufferedReader(InputStreamReader(socket.inputStream))

                    val request = "{\"cmd\":\"get_ui_dump\"}"
                    writer.println(request)

                    val responseStr = reader.readLine()
                    println("Response length: ${responseStr?.length}")
                    
                    assert(responseStr != null && responseStr.contains("ok"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                assert(false) { "Failed to connect to agent: ${e.message}" }
            }
        }
    }

    @Test
    fun testFindObject() {
        runBlocking {
            println("Testing findObject using UiDevice...")
            val device = UiDevice(adb)
            device.pressHome()
            delay(1000)
            val obj = device.findObject(By.text("Gmail"))
            println("Found object: ${obj?.getText()}")
            assert(obj != null)
            assert(obj?.getText() == "Gmail")
        }
    }

    @Test
    fun testNavigateToDeviceUnlock() {
        runBlocking {
            println("Testing navigation to Device Unlock...")
            val device = UiDevice(adb)
            
            // Open Security Settings
            adb.adb.execute(com.malinskiy.adam.request.shell.v1.ShellCommandRequest("am start -a android.settings.SECURITY_SETTINGS"), adb.deviceSerial)
            delay(2000) // Wait for load
            
            // Find the item with wait and textContains
            val obj = device.wait(By.textContains("画面ロック"), 5000)
            println("Found summary item: ${obj != null}")
            assert(obj != null) { "Summary item not found!" }
            
            // Click it
            obj?.click()
            delay(2000) // Wait for transition
            
            // Verify we are on the next screen
            val newDump = device.findObject(By.pkg("com.android.settings"))
            println("Still in settings: ${newDump != null}")
            assert(newDump != null)
        }
    }
}
