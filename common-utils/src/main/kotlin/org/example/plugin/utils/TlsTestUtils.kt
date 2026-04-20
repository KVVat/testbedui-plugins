package org.example.plugin.utils

import com.malinskiy.adam.AndroidDebugBridgeClient
import com.malinskiy.adam.request.shell.v1.ShellCommandRequest
import kotlinx.coroutines.runBlocking
import org.example.project.JUnitBridge
import org.junit.Assert
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Shared utilities for TLS tests.
 */
object TlsTestUtils {

    /**
     * Triggers the client app to connect to a URL and captures the result from logcat.
     * Uses JUnitBridge.baseDir to resolve the path to the helper APK.
     */
    fun tlsCapturePacket(client: AndroidDebugBridgeClient, serial: String, testlabel: String, testurl: String): Pair<String, Path> {
        var pcap: Path = Paths.get("/")
        var httpResp: String = ""

        runBlocking {
            // Use resourceDir as intended by the project structure
            val browserApk = File(org.example.project.JUnitBridge.resourceDir, "openurl-debug.apk")
            println("[Utils] Installing APK from: ${browserApk.absolutePath}")
            
            val ret = AdamUtils.installApk(client, serial, browserApk, true)
            Assert.assertTrue("Failed to install openurl app: ${ret}", ret.startsWith("Success"))
            
            println("[Utils] Force stopping app...")
            client.execute(ShellCommandRequest("am force-stop com.example.openurl"), serial)
            Thread.sleep(2000)
            
            // Trigger connection via intent using both data URI and extra
            val cmd = "am start -a android.intent.action.VIEW -n com.example.openurl/.MainActivity -d \"$testurl\" --es openurl \"$testurl\""
            println("[Utils] Running command: $cmd")
            client.execute(ShellCommandRequest(cmd), serial)

            // Wait for app to finish processing and log result
            Thread.sleep(5000)

            // Dump logcat using su 0 to ensure we see all logs
            val logcatResult = client.execute(ShellCommandRequest("su 0 logcat -d"), serial)
            val logcatOutput = String(logcatResult.stdout)
            
            val lines = logcatOutput.split("\n")
            
            // Find all lines containing "worker@"
            val workerLines = lines.filter { it.contains("worker@") }
            if (workerLines.isNotEmpty()) {
                org.example.project.JUnitBridge.logging?.invoke("--- Worker Logs Start ---", org.example.project.TestLogLevel.INFO)
                workerLines.forEach { line ->
                    org.example.project.JUnitBridge.logging?.invoke(line, org.example.project.TestLogLevel.INFO)
                }
                org.example.project.JUnitBridge.logging?.invoke("--- Worker Logs End ---", org.example.project.TestLogLevel.INFO)
            }
            
            val matchedLine = lines.findLast { it.contains("worker@return") }
            
            if (matchedLine != null) {
                val marker = "worker@return"
                val index = matchedLine.lastIndexOf(marker)
                if (index >= 0) {
                    httpResp = matchedLine.substring(index + marker.length).trim()
                } else {
                    httpResp = matchedLine.trim()
                }
                org.example.project.JUnitBridge.logging?.invoke("Extracted response: $httpResp", org.example.project.TestLogLevel.INFO)
            } else {
                org.example.project.JUnitBridge.logging?.invoke("Marker worker@return not found in logcat", org.example.project.TestLogLevel.ERROR)
                httpResp = "Error: marker not found in logcat"
            }
        }
        return Pair(httpResp, pcap)
    }
}
