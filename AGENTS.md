# エージェント実行のためのプロジェクト知見

## 1. TestBed Core (MCP サーバー) の起動と停止
このプロジェクトでは、`testbed-core` が提供する MCP サーバー（`localhost:11452`）と通信して、デバイス情報を取得したり操作したりします。
エージェントがツールを正常に利用するためには、事前に `testbed-core` デスクトップアプリが起動している必要があります。

### 起動方法
別ターミナルで `testbed-core` リポジトリのディレクトリに移動し、以下のコマンドを実行して起動します。

```bash
cd ../testbed-core
./gradlew :composeApp:run
```

### 停止（キル）方法
二重起動を防ぐため、またはポート競合（`Address already in use` 等）が発生した場合は、11452 番ポートを使用しているプロセスを特定して強制終了します。
エージェントが自動作業する前にも、このコマンドでクリーンアップしてから起動すると安全です。

```bash
# 11452ポートを使っているプロセスをキル
lsof -t -i :11452 | xargs kill -9
```

## 2. ドキュメント (MDFPP-CC) の参照
`testbed-core` と同様に、このリポジトリにも `docs`（実体は `KVVat/mdfpp-docs.git`）が Git サブモジュールとして追加されています。
CC (Common Criteria) などのセキュリティドキュメントを参照して実装を進める場合は、`docs/MDFPP-CC/` 以下の Markdown ファイルを直接読み込んでください。

サブモジュールが空の場合や最新化したい場合は以下を実行します。
```bash

## 3. ドキュメント駆動のテスト開発（至上命題）
このプロジェクト（`testbedui-plugins`）における最大の指針は、**セキュリティ要件ドキュメントに厳格に従ったテスト実装**です。
- LLMエージェントは自律的にテストを記述する際、必ず事前に `docs/MDFPP-CC/` 以下のMarkdownファイルから該当する機能要件や評価基準（Test Case）を読み込んでください。
- 実装するJUnitテストは、ドキュメントに定義されたPass/Fail判定基準に合致している必要があります。

## 4. MCP経由での自律的テスト実行ワークフロー
LLMエージェントがテストを開発・実行する際は、以下のワークフローに従い `testbed-core` のMCPツールを活用します。

1. **ドキュメント参照**: `docs/MDFPP-CC/` から要件を取得
2. **コード生成**: このリポジトリ（`testbedui-plugins`）側にテストコード（Kotlin/JUnit）を追加し、ビルド（JAR化）を行う
3. **テストの再読み込み (`junit_test_reload`)**:
   `testbed-core` 側のMCPサーバーに対して `junit_test_reload` をコールし、生成したJARを動的にロードさせる
4. **テストの存在確認 (`junit_test_list`)**:
   `junit_test_list` をコールして、目的のテストクラス・メソッドが正しくロードされたか一覧から探す
5. **テスト実行 (`junit_test_execute` & `junit_test_receive`)**:
   `junit_test_execute` で対象のテストを開始し、`junit_test_receive` で結果（Pass/Fail, ログ, AsserionError）を受け取る
6. **フィードバックループ**:
   結果がFailだった場合は、デバイスのSensingツール（`get_ui_dump`など）や実行結果を分析してテストコードを修正し、手順2に戻る。
> **Note (testbed-core エージェントへの通達)**:
> 現在のところ `testbed-core` 側に上記のテスト制御ツールのエンドポイントが未整備です。
> LLMが自律的にテストを回せるよう、以下の機能・仕様を備えたツールの優先実装をお願いします：
> 
> 1. **`check_testbed_health`**: ADB接続、対象デバイスのオンライン状態、エージェントステータスを一括で確認できること。
> 2. **`junit_test_reload`**: LLMがビルドし転送したテストJAR（`testbedui-plugins` 側から）を即座に再読み込みできること。
> 3. **`junit_test_list`**: 読み込んだテストクラス・メソッドの一覧をJSON配列等で確実に返却できること。
> 4. **`junit_test_execute`**: 特定のテストクラス/メソッドを指定して実行を開始できること。
> 5. **`junit_test_receive`**: テストの実行結果をポーリング等の仕組みで取得できること。
> 
> **[結果取得時の強い要望事項]**
> - 実装する際は、LLMがパースしやすいように**必ずJSON形式で構造化**して返却すること。
> - テスト結果には必ず `status`（Pass/Fail/Error）を含めること。
> - Failの場合は、`assertion_msg` (AssertionErrorの理由) と `stacktrace` を含めること。
> - これにより、このリポジトリ（`testbedui-plugins`）側で「なぜ失敗したか」を正確に解釈し、自律的なコード修正が可能になります。
> 
> **[テスト実行ログの要望事項]**
> - テスト内で `logi()` / `loge()` / `logp()` 等で出力されたログ（テスト途中の進行状況メッセージ）を、テスト結果とともに `log` フィールドとして返却すること。
> - これにより、LLMは「テストのどの段階で失敗したか」を正確に把握でき、テストコードの修正精度が大幅に向上する。
> 
> **[ストリーミング対応の要望事項]**
> - 長時間テスト（UIインタラクションを伴うテスト等）では、`junit_test_receive` の呼び出しごとに**途中経過のログ**が返されることが望ましい。
> - 具体的には、`status: "Running"` の場合にも `log` フィールドに直近のログ出力を含めることで、LLMがテストの進行状況をリアルタイムに監視し、ハングアップ等の検知やタイムアウト判断ができるようになる。
> - 期待するレスポンス例:
>   ```json
>   {"status":"Running", "log":["[INFO] ADBデバイス接続確認...", "[INFO] APKインストール中..."]}
>   {"status":"Finished", "results":[{"class_name":"...", "method_name":"...", "status":"Fail", "assertion_msg":"...", "stacktrace":"...", "log":["...", "..."]}]}
>   ```
