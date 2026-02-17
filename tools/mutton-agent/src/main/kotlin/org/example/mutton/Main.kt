package org.example.agent

import android.app.Instrumentation
import android.os.Looper
import android.os.Build
//import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.json.JSONObject
import java.util.Scanner

/**
 * app_process から起動されるエントリポイント
 */
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        // メインスレッドのLooper準備 (Handlerなどが動くように)
        Looper.prepareMainLooper()

        println(">>> AGENT_STARTED")

        // Instrumentationの取得 (これが魔法の鍵)
        // シェルから起動した場合、Instrumentationは自動ではアタッチされませんが、
        // UiAutomationを取得するためにリフレクションやShell権限を利用します。
        // ※ app_process独自起動の場合、UiAutomation.getInstance() を直接呼ぶか、
        // "am instrument" 経由で起動するかの2択になります。
        // 今回は "Shell権限で動くJavaプログラム" なので、UiAutomationConnection を自前で繋ぐ高度な実装が必要です。

        // 簡易実装: まずは単純なループで生存確認
        val scanner = Scanner(System.`in`)
        while (scanner.hasNextLine()) {
            val command = scanner.nextLine().trim()
            if (command == "EXIT") break

            handleCommand(command)
        }

        println(">>> AGENT_STOPPED")
    }

    private fun handleCommand(cmd: String) {
        try {
            when (cmd) {
                "PING" -> sendJson("PONG", "status" to "alive")
                "DUMP" -> {
                    // ここで階層ダンプを実行
                    // val rootNode = AccessibilityInteractionClient...
                    sendJson("DUMP_RESULT", "xml" to "<dummy>TODO: Implement Dump</dummy>")
                }
                else -> sendJson("ERROR", "message" to "Unknown command: $cmd")
            }
        } catch (e: Exception) {
            sendJson("ERROR", "message" to e.toString())
        }
    }

    private fun sendJson(type: String, vararg pairs: Pair<String, Any>) {
        val json = JSONObject()
        json.put("type", type)
        pairs.forEach { json.put(it.first, it.second) }
        // 標準出力に1行のJSONとして吐く (クライアント側でパースする)
        println(json.toString())
    }
}