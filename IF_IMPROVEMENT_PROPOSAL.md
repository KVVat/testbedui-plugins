# Interface Improvement Proposal: Test Progress & Logging

## 1. 現状の課題 (Current Issues)
現在の `mutton-agent` MCP インターフェースには以下の課題があります：

*   **経過の不透明性**: `junit_test_execute` 後、`junit_test_receive` で結果が返るまで、テストがどのステップで止まっているか（あるいは順調に進んでいるか）が全くわからない。
*   **長時間テストへの不安**: Android テストは APK のインストールや待機（`delay`, `waitLogcatLine`）を含むため、数分かかる場合がある。この間、ユーザーや AI アシスタントはハングアップとの区別がつかない。
*   **デバッグの困難さ**: テストが失敗した場合、最終的なスタックトレースしか得られず、そこに至るまでの `logi`, `logp` 等のカスタムログ（`AdamUtils` 経由など）が欠落している。

## 2. 改善案 (Proposed Improvements)

### A. ポーリング型ログ取得の拡張 (`junit_test_receive` の強化)
`junit_test_receive` を、テスト終了後だけでなく、**実行中も呼び出し可能**にします。

*   **仕様変更**:
    *   `status: "Running"` の場合でも、その時点までに蓄積されたログ（stdout/stderr/custom logs）を返却する。
    *   `last_log_id` などのパラメータを渡すことで、差分のみを取得できるようにする。

```json
// junit_test_receive のレスポンス例 (実行中)
{
  "status": "Running",
  "logs": [
    {"time": "10:00:01", "level": "INFO", "message": "Installing target APK..."},
    {"time": "10:00:05", "level": "SUCCESS", "message": "Target APK installed."}
  ],
  "current_step": "Installing apps",
  "progress_percent": 20
}
```

### B. 進捗報告 API (`JUnitBridge` の拡張)
テストコード（Kotlin）側から、現在のステップや進捗率を明示的に報告できるようにします。

*   **実装イメージ**:
    ```kotlin
    JUnitBridge.reportProgress(step = "Preparing Data", percent = 40)
    logp("Step 1 complete")
    ```

### C. ログストリーミング (Server-Sent Events / WebSocket)
MCP サーバーからクライアントへ、ログをリアルタイムにプッシュします。

*   **メリット**: AI アシスタントがログをストリーミング表示でき、ユーザー体験が大幅に向上する。
*   **実装**: `mutton-agent` に SSE (Server-Sent Events) エンドポイントを追加し、テスト実行中のログを順次配信する。

### D. Logcat 統合
`AdamUtils.waitLogcatLine` で待機している際、対象のタグのログを自動的に経過ログに含める。

## 3. 期待される効果
*   **信頼性の向上**: テストが進行中であることを確信できる。
*   **迅速なエラー検知**: タイムアウトを待たずとも、ログの異常から早期に中止判断ができる。
*   **AI による動的な対応**: ログの内容に基づき、AI がテストの進行状況をリアルタイムで解説したり、途中で介入したりすることが可能になる。
