# テスト作成規約 (Test Conventions)

このプロジェクトにおけるテスト作成の規約と、利用可能なツールについてまとめます。

## 1. ログ出力 (Logging)

テスト中のログ出力には、`org.example.plugin.utils` パッケージで定義されている以下の拡張関数を使用してください。
`JUnitBridge.logging?.invoke(...)` を直接呼び出す必要はありません。

- `logd(message)`: デバッグ情報 (DEBUG レベル)
- `logi(message)`: 一般的な情報 (INFO レベル)
- `logp(message)`: テスト成功時の情報 (PASS レベル)
- `logw(message)`: 警告 (WARN レベル)
- `loge(message)`: エラー情報 (ERROR レベル)

### 使用例
```kotlin
import org.example.plugin.utils.*

@Test
fun myTest() {
    logi("テストを開始します")
    // ...
    logp("期待通りの結果が得られました")
}
```

## 2. テストの基本構成

- **JUnit 4**: `org.junit.Test` アノテーションを使用します。
- **AdbDeviceRule**: デバイス操作が必要な場合は、`AdbDeviceRule` を `@get:Rule` として定義します。

```kotlin
class SampleTest {
    @get:Rule
    val adbDeviceRule = AdbDeviceRule()

    @Test
    fun testWithDevice() {
        val serial = adbDeviceRule.deviceSerial
        logi("Target device: $serial")
    }
}
```

## 3. テストの実行とデバッグ

### ビルドと反映
テストコードを修正した後は、以下の手順で反映させます。
1. `./gradlew :test-sample:jar` (または対象モジュールの jar タスク)
2. `junit_test_reload` ツールの実行

### リアルタイムログの取得
テスト実行中は `junit_test_receive` を定期的に呼び出すことで、進行中のログをリアルタイムに取得できます。

## 4. 注意点
- **長時間実行**: 長時間かかるテスト（2分以上など）を作成する場合は、進行状況がわかるように定期的に `logi` などで進捗を出力することを推奨します。
- **例外処理**: テスト中に例外を投げると、テストは失敗として記録され、スタックトレースがログに含まれます。
