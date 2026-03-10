```python
import re

# Read FCS_NAME_CONVERSION.md
try:
    with open("FCS_NAME_CONVERSION.md", "r", encoding="utf-8") as f:
        print("--- FCS_NAME_CONVERSION.md ---")
        print(f.read())
except Exception as e:
    print(f"Error reading FCS_NAME_CONVERSION.md: {e}")

# Also let's check FDP_ACC and FDP_ACF in PP_MDF_V3.3.md to clarify the difference
with open("PP_MDF_V3.3.md", "r", encoding="utf-8") as f:
    pp_content = f.read()

print("--- FDP_ACC in PP ---")
for match in re.finditer(r'\*\*FDP_ACC.*?\*\*', pp_content):
    print(match.group(0))

print("--- FDP_ACF in PP ---")
for match in re.finditer(r'\*\*FDP_ACF.*?\*\*', pp_content):
    print(match.group(0))



```

```text
Error reading FCS_NAME_CONVERSION.md: [Errno 2] No such file or directory: 'FCS_NAME_CONVERSION.md'
--- FDP_ACC in PP ---
--- FDP_ACF in PP ---
**FDP_ACF_EXT.1 Access Control for System Services**
**FDP_ACF_EXT.3 Security Attribute Based Access Control**
**FDP_ACF_EXT.2 Access Control for System Resources**
**FDP_ACF_EXT.1.2.**
**FDP_ACF_EXT.1 Access Control for System Services**
**FDP_ACF_EXT.1.1**
**FDP_ACF_EXT.1.2**
**FDP_ACF_EXT.2 Access Control for System Resources**
**FDP_ACF_EXT.2.1**
**FDP_ACF_EXT.3 Security Attribute Based Access Control**
**FDP_ACF_EXT.3.1**


```

```python
with open("kvvat/testbedui-plugins/testbedui-plugins-5d7a27ccb13c847b0b2264259833bd6c029abe21/FCS_NAME_CONVERSION.md", "r", encoding="utf-8") as f:
    print(f.read()[:1000])


```

```text
# FSR ID to Kotlin Class Naming Convention

This document defines the naming rules for converting Functional Safety Requirement (FSR) IDs (e.g., from Common Criteria or Protection Profiles) into Kotlin test class names within the `testbedui-plugins` project.

## 1. Basic Principle: UpperCamelCase (PascalCase)

* **Format:** Convert the ID into **UpperCamelCase**.
* **Separators:** All separators (`_`, `.`, `/`) are removed. They serve as boundaries for capitalization.
* **Suffix:** Append `Test` to the end of the class name.

## 2. Specific Rules

### 2.1 Underscores (`_`) and Dots (`.`)
Remove them completely. The letter immediately following a separator is capitalized.

* `FDP_ACC.1` -> `FdpAcc1`

### 2.2 Slashes (`/`)
Slashes usually denote a refinement or specific configuration (e.g., "Low", "TLS").
**Rule:** Remove the slash and capitalize the following word. Treat it as a suffix to the main ID.

* `FCS_CKH.1/Low` -> `FcsCkh1Low`

### 2.3 "EXT" (Extension)
Treat "EXT" as a regular


```

おっしゃる通り、ご指摘の点は **Common Criteria（CC）の基本構造と、MDF PPの独自拡張（EXT）による要件定義のズレ** に起因するものです。

### `FDP_ACC` と `FDP_ACF_EXT` の違いと命名のブレについて

知識ベース（`CCPART2V3.1R5.md`）によれば、CCにおけるアクセス制御は通常、以下の2つで一対となります。

* **FDP_ACC (Access Control Policy):** 「誰が・何に・どういう条件でアクセスできるか」という**ポリシー（方針）**の定義。
* **FDP_ACF (Access Control Functions):** そのポリシーを実際に評価・執行する**機能**の定義。

しかし、優先度1位の `PP_MDF_V3.3.md` では、標準のCC要件を拡張した **`FDP_ACF_EXT.1`** (Access Control for System Services) や **`FDP_ACF_EXT.2`** などの拡張要件（EXT）として、具体的なAndroidのパーミッションモデルやサンドボックス機構のテストを直接定義しています。
そのため、過去のMDF PPのバージョンや、ベースとなるCC要件名（FDP_ACC.1）を意識して、プロジェクト内でテストケースクラス名（`FdpAcc1Test.kt`など）として命名・実装されているものと推測されます。**実質的なテスト内容（サンドボックスや権限検証）はFDP_ACF_EXTの要件と同一**と考えて問題ありません。

---

### 前回の提案内容のまとめ表

これまで挙げた「UI操作不要・ADBコマンドとLogcat監視のみで自動化可能なテスト」を表にまとめます。

| テスト対象 (MDF PP v3.3要件) | 該当するSFR | テストの実行手順 (UI不要) | Logcatでの合否判定（Pass条件） |
| --- | --- | --- | --- |
| **アプリ間のデータアクセス制御**<br>

<br>(サンドボックスの保護) | FDP_ACF_EXT.1.2 | ターゲットアプリがデータを保存後、アタッカーアプリ（別UID）からそのファイルパスへの読み取りを試行する。 | アタッカー側で `Permission Denial` または `SecurityException` が出力され、アクセスが拒否されること。 |
| **システムサービスへのアクセス制御**<br>

<br>(パーミッション保護) | FDP_ACF_EXT.1.1 | 必要な権限（GPSやカメラ等）を持たないテストアプリを実行し、対象サービスを呼び出す。 | サービス呼び出し時に `SecurityException` が出力され、OSによりブロックされること。 |
| **アプリのデジタル署名検証**<br>

<br>(不正APKの排除) | FPT_TUD_EXT.5.1 | ADB経由で「無署名」「不正証明書」のAPKを `adb install` する。 | `INSTALL_PARSE_FAILED_NO_CERTIFICATES` 等のエラーログが出力され、インストールが失敗すること。 |
| **ドメイン分離・不正書き込み防止**<br>

<br>(システム領域の保護) | FPT_AEX_EXT.4.1 | テストアプリにシステムディレクトリ（`/system`等）へのファイル書き込みを試行させる。 | `Read-only file system` や `EACCES` エラーが出力され、書き込み成功のログが**出ない**こと。 |
| **X.509 証明書パスの検証**<br>

<br>(不正なTLS通信の遮断) | FIA_X509_EXT.1 | テストアプリに、有効期限切れ等の不正な証明書を持つサーバー([https://expired.badssl.com](https://expired.badssl.com)等)へ通信させる。 | 通信レイヤーで `SSLHandshakeException` や `CertPathValidatorException` が発生し、通信が遮断されること。 |

---

### 新たに追加で実現できそうなテストケース

リポジトリ内の既存ファイル（`FprPse1Test.kt` や `FcsCkhExt1HighCredentialsTest.kt` など）の構成も踏まえ、MDF PP V3.3から同様のアプローチで自動化できそうなものを洗い出しました。

#### 6. デバイス識別子の不可逆化・アクセス制限（プライバシー保護）

* **該当SFR:** **FPR_PSE.1** (Pseudonymity)
* **要件概要 (Test 146):** アプリケーションがハードウェアの一意な識別子（MACアドレス、IMEIなど）を取得しようとした際、本物の値が返されないこと。
* **テスト手法:** 権限を持たない通常のテストアプリから `TelephonyManager.getDeviceId()` や `WifiInfo.getMacAddress()` を呼び出す。
* **Logcat判定:** `SecurityException` が発生するか、Androidの仕様通り仮のMACアドレス（`02:00:00:00:00:00`）がログに出力されれば **Pass**。

#### 7. 暗号鍵の生成（Keystore/StrongBoxの動作確認）

* **該当SFR:** **FCS_CKM.1** / **FCS_CKH_EXT.1** (Cryptographic Key Generation)
* **要件概要:** TSFが指定されたアルゴリズムと鍵長（例：AES-256）で暗号鍵を正しく生成できること。
* **テスト手法:** テスト用アプリを起動し、Android Keystore（可能ならHardwareBacked/StrongBox指定）で鍵生成API（`KeyGenerator`）を実行させる。
* **Logcat判定:** Keystore内部の例外（`KeystoreException`等）が発生せず、「鍵生成完了」を示すアプリ側の任意タグログ（例: `TestApp: KeyGeneratedSuccessfully`）が出力されれば **Pass**。

#### 8. Data-at-Rest（保存データ）の暗号化

* **該当SFR:** **FDP_DAR_EXT.1** または **FDP_DAR_EXT.2**
* **要件概要:** ユーザーデータがフラッシュメモリ上で暗号化されて保存される機構（File-Based Encryption等）。
* **テスト手法:** 1. テストアプリで `EncryptedSharedPreferences` や Keystoreの鍵を用いた暗号化ファイル書き込みを行う。
2. 書き込み成功ログをLogcatで確認する。
   （※リポジトリ内の `FdpDarExt1Test.kt` にも同様の意図が含まれていると見受けられます）
* **Logcat判定:** アプリ側で暗号化・復号のラウンドトリップ処理を行い、平文が一致した旨のログ（例: `TestApp: EncryptionDecryptionSuccess`）が出力されれば **Pass**。

これらのテストは、GUIオートメーション（AppiumやUIAutomator）が抱える「画面レイアウト変更によるテスト崩れ」や「環境依存の描画遅延によるタイムアウト」を完全に排除できるため、CI/CDパイプライン上で安定して実行できる強みがあります。