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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

/**
 * app_process から起動されるエントリポイント
 * ソケットサーバーとして常駐し、JSONコマンドを処理します。
 */
object Main {

    private const val SOCKET_NAME = "mutton_agent"

    private lateinit var instrumentation: Instrumentation
    private lateinit var device: UiDevice

    @JvmStatic
    fun main(args: Array<String>) {
        // 1. Looper準備 (AndroidのシステムAPIを叩くために必須)
        Looper.prepareMainLooper()

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
                    processCommand(cmdJson)
                } catch (e: Exception) {
                    createError("Invalid JSON: ${e.message}")
                }

                // 応答を送信
                writer.println(response.toString())
                println("TX: $response")

                // 次の行を読む
                line = reader.readLine()
            }
        }
        println(">>> Client disconnected")
    }

    private fun processCommand(json: JSONObject): JSONObject {
        val cmd = json.optString("cmd")
        return when (cmd) {
            "ping" -> {
                JSONObject().put("status", "pong").put("message", "I am alive!")
            }
            "dump" -> {
                // UI階層ダンプの実装予定地
                // 実際にはここで AccessibilityInteractionClient 等を使ってダンプする
                device.waitForIdle()
                val activeNode = instrumentation.uiAutomation.rootInActiveWindow
                if (activeNode != null) {
                    val rootNode = JsonUiDumper().dumpNodeRec(activeNode, 0)
                    JSONObject()
                        .put("type", "dump_result")
                        .put("status", "ok")
                        .put("output",Json.encodeToString(rootNode))

                } else {
                    // 画面取得失敗時
                    //call.respond(io.ktor.http.HttpStatusCode.InternalServerError, "Failed to get root node")
                    JSONObject()
                        .put("type", "dump_result")
                        .put("status", "ng")
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