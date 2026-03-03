package org.example.mutton
import android.app.Instrumentation
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.example.mutton.uidumper.JsonUiDumper
import org.json.JSONObject
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * Android Instrumentation Test として起動されるエントリポイント
 * ソケットサーバーとして常駐し、JSONコマンドを処理します。
 */
@RunWith(AndroidJUnit4::class)
class AgentTest {

    private val SOCKET_NAME = "mutton_agent"

    private lateinit var instrumentation: Instrumentation
    private lateinit var device: UiDevice

    @Volatile
    private var isImageStreaming = false
    private var imageStreamThread: Thread? = null

    @Volatile
    private var isDumpStreaming = false
    private var dumpStreamThread: Thread? = null

    @Test
    fun startServer() {

        println(">>> AGENT_STARTED (Socket Mode)")

        // 2. ソケットサーバーの立ち上げ
        // AdbObserver側で "mutton_agent" という名前の Abstract Socket に転送しているため
        // ここでも同じ名前で待ち受ける必要があります。
        // LocalServerSocket(name) はデフォルトで Linux Abstract Namespace にソケットを作ります。
        try {
            val server = LocalServerSocket(SOCKET_NAME)
            println(">>> Listening on localabstract:$SOCKET_NAME")
            instrumentation = InstrumentationRegistry.getInstrumentation()
            device = UiDevice.getInstance(instrumentation)

            // 接続待ちループ
            while (true) {
                try {
                    // クライアントからの接続を待機 (ブロッキング)
                    val client = server.accept()
                    println(">>> Client connected")

                    // クライアントとの通信処理 (簡易的にメインスレッドで処理)
                    // 本格的にやるなら別スレッドに逃がすが、1対1通信ならこれでもOK
                    handleClient(client)

                } catch (e: Exception) {
                    println(">>> Connection error: ${e.message}")
                    e.printStackTrace()
                    // 致命的なエラーでない限りループを継続
                }
            }
        } catch (e: Exception) {
            println(">>> Fatal error: ${e.message}")
            e.printStackTrace()
        }

        println(">>> AGENT_STOPPED")
    }

    private fun handleClient(client: LocalSocket) {
        // useブロックで自動的にクローズ
        client.use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val writer = PrintWriter(socket.outputStream, true)

            try {
                // 行単位でコマンドを受信
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.isBlank()) {
                        line = reader.readLine()
                        continue
                    }

                    println("RX: $line") // デバッグログ

                    val response = try {
                        val cmdJson = JSONObject(line)
                        processCommand(cmdJson, writer)
                    } catch (e: Exception) {
                        createError("Invalid JSON: ${e.message}")
                    }

                    // 応答を送信
                    writer.println(response.toString())
                    println("TX: $response")

                    // 次の行を読む
                    line = reader.readLine()
                }
            } finally {
                // クライアント切断時にストリームを確実に停止
                isImageStreaming = false
                imageStreamThread?.interrupt()
                imageStreamThread = null
                
                isDumpStreaming = false
                dumpStreamThread?.interrupt()
                dumpStreamThread = null
            }
        }
        println(">>> Client disconnected")
    }

    private fun processCommand(json: JSONObject, writer: PrintWriter): JSONObject {
        val cmd = json.optString("cmd")
        return when (cmd) {
            "start_stream" -> {
                val fps = json.optDouble("fps", 1.0)
                val delayMs = (1000.0 / fps).toLong()

                if (!isImageStreaming) {
                    isImageStreaming = true
                    imageStreamThread = Thread {
                        while (isImageStreaming) {
                            try {
                                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                                if (bitmap != null) {
                                    val stream = java.io.ByteArrayOutputStream()
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, stream)
                                    val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                                    val frameJson = JSONObject()
                                        .put("type", "stream_frame")
                                        .put("data", base64)
                                    
                                    // PrintWriter internally synchronizes print/println operations
                                    writer.println(frameJson.toString())
                                    bitmap.recycle()
                                }
                                Thread.sleep(delayMs)
                            } catch (e: InterruptedException) {
                                break
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    imageStreamThread?.start()
                    JSONObject().put("status", "ok").put("message", "Screen stream started at $fps fps")
                } else {
                    createError("Screen stream is already running")
                }
            }
            "stop_stream" -> {
                isImageStreaming = false
                imageStreamThread?.interrupt()
                imageStreamThread = null
                JSONObject().put("status", "ok").put("message", "Screen stream stopped")
            }
            "start_dump_stream" -> {
                val fps = json.optDouble("fps", 1.0)
                val delayMs = (1000.0 / fps).toLong()

                if (!isDumpStreaming) {
                    isDumpStreaming = true
                    dumpStreamThread = Thread {
                        while (isDumpStreaming) {
                            try {
                                device.waitForIdle()
                                val activeNode = instrumentation.uiAutomation.rootInActiveWindow
                                if (activeNode != null) {
                                    val rootNode = JsonUiDumper().dumpNodeRec(activeNode, 0)
                                    val dumpJson = JSONObject()
                                        .put("type", "dump_stream_frame")
                                        .put("data", Json.encodeToString(rootNode))
                                    
                                    writer.println(dumpJson.toString())
                                } else {
                                    val errJson = JSONObject()
                                        .put("type", "dump_stream_frame")
                                        .put("error", "activeNode is null. Screen might be off or no active window.")
                                    writer.println(errJson.toString())
                                }
                                Thread.sleep(delayMs)
                            } catch (e: InterruptedException) {
                                break
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    dumpStreamThread?.start()
                    JSONObject().put("status", "ok").put("message", "Dump stream started at $fps fps")
                } else {
                    createError("Dump stream is already running")
                }
            }
            "stop_dump_stream" -> {
                isDumpStreaming = false
                dumpStreamThread?.interrupt()
                dumpStreamThread = null
                JSONObject().put("status", "ok").put("message", "Dump stream stopped")
            }
            "ping" -> {
                JSONObject().put("status", "pong").put("message", "I am alive!")
            }
            "dump" -> {
                device.waitForIdle()
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                var base64: String? = null
                if (bitmap != null) {
                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, stream)
                    base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                    bitmap.recycle()
                }

                val activeNode = instrumentation.uiAutomation.rootInActiveWindow
                if (activeNode != null) {
                    val rootNode = JsonUiDumper().dumpNodeRec(activeNode, 0)
                    val json = JSONObject()
                        .put("type", "dump_result")
                        .put("status", "ok")
                        .put("output", Json.encodeToString(rootNode))
                    if (base64 != null) {
                        json.put("screenshot", base64)
                    }
                    json
                } else {
                    val json = JSONObject()
                        .put("type", "dump_result")
                        .put("status", "ng")
                        .put("message", "instrumentation.uiAutomation.rootInActiveWindow returned null. Active window might not be accessible.")
                    if (base64 != null) {
                        json.put("screenshot", base64)
                    }
                    json
                }
            }
            "shell" -> {
                val commandStr = json.getString("args") // 例: "ls -l /sdcard"
                // Javaの標準機能でプロセス実行
                val process = Runtime.getRuntime().exec(commandStr)
                val output = process.inputStream.bufferedReader().use { it.readText() }
                JSONObject().put("status", "ok").put("output", output)
            }
            "exit" -> {
                // プロセス終了用
                System.exit(0)
                JSONObject().put("status", "exiting")
            }
            else -> createError("Unknown command: $cmd")
        }
    }

    private fun createError(msg: String): JSONObject {
        return JSONObject().put("status", "error").put("message", msg)
    }
}