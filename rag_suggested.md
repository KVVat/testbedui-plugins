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


MDF_PP Evaluation Helperとして、過去の議論と提供された知識ベース（特に`PP_MDF_V3.3.md`と、実装例である`st-google-android15.md`）に基づき、**「UI操作不要で、ADBとLogcatのみで合否判定（Pass/Fail）が可能な自動テスト手順」**を具体化してまとめます。

GoogleのSecurity Target（ST）の記述を踏まえることで、Android 15におけるOS標準のセキュリティ機構（TSF）がどう機能するかが明確になり、より正確なLogcatの監視対象（例外やエラーコード）を特定できます。

---

### 1. アプリケーションサンドボックスとデータ隔離検証

* **Requirement:** **FDP_ACF_EXT.1.2** (Access Control for System Services)
* **Google ST (Android 15) Context:**
  GoogleのSTでは、LinuxのDiscretionary Access Control (DAC) とSELinuxを用いて、各アプリケーションに一意のUID（User ID）を割り当て、他のアプリのプライベートデータ領域（`/data/data/<package_name>`）へのアクセスをOSレベルで遮断していると記述されています。
* **具体的なテスト手順 (UI不要):**
1. `adb install` でターゲットアプリ（`assets-target-app`）とアタッカーアプリ（`assets-attacker-app`）をインストールする。
2. `adb shell am start-foreground-service`（またはブロードキャスト）でターゲットアプリをバックグラウンド起動し、自身の内部ストレージにテスト用ファイル（例: `secret.txt`）を書き込ませる。
3. `adb shell am start-foreground-service` でアタッカーアプリを起動し、ターゲットアプリの絶対パス（`/data/data/org.example.assets.target/files/secret.txt`）の読み取り（`FileInputStream`）を試行させる。


* **Logcatでの合否判定:**
* **Pass:** アタッカーアプリ側で `java.io.FileNotFoundException: ... (Permission denied)` または `EACCES` の例外ログがLogcatに出力され、ファイルの内容が取得できないこと。



### 2. 平文通信（HTTP）のブロック検証

* **Requirement:** **FTP_ITC_EXT.1** (Trusted Channel Communication) / **FDP_UPC_EXT.1/APPS**
* **Google ST (Android 15) Context:**
  STでは、Androidの「Network Security Configuration」により、デフォルトで平文のトラフィック（Cleartext traffic）がブロックされ、BoringSSLを用いたTLS通信が強制されると記載されています。
* **具体的なテスト手順 (UI不要):**
1. ネットワーク通信を行うテストアプリを `adb install` する（マニフェストで `usesCleartextTraffic="true"` のような意図的なダウングレードをしていない標準状態のAPK）。
2. `adb shell am broadcast -a com.example.action.TEST_HTTP --es url "http://example.com"` のように、インテントのExtrasでHTTP（非TLS）のURLを渡し、バックグラウンドで `HttpURLConnection` を実行させる。


* **Logcatでの合否判定:**
* **Pass:** OSのネットワークセキュリティポリシーによって通信が遮断され、Logcatに `java.net.UnknownServiceException: CLEARTEXT communication to example.com not permitted by network security policy` が出力されること。



### 3. デジタル署名と不正な証明書の検証

* **Requirement:** **FPT_TUD_EXT.5.1** (Application Verification)
* **Google ST (Android 15) Context:**
  TOE（Android OS）のPackageManagerサービスが、APKのインストール前にAPK Signature Scheme（v2/v3等）を用いて暗号学的に署名と証明書の有効性を検証すると定義されています。
* **具体的なテスト手順 (UI不要):**
1. 事前にホストPC側で3つのAPKを用意する。
* APK_A: 未署名のAPK
* APK_B: 有効期限切れの証明書、または改ざんされた署名を持つAPK
* APK_C: 適切に署名された正当なAPK


2. ADBコマンドで順にインストールを実行する。
* `adb install APK_A.apk`
* `adb install APK_B.apk`




* **Logcat (またはADB標準出力) での合否判定:**
* **Pass:** 実行時の標準エラー出力、およびLogcatのPackageManagerのログに `INSTALL_PARSE_FAILED_NO_CERTIFICATES` や `INSTALL_FAILED_INVALID_APK` などのエラーが出力され、インストールプロセスが失敗（Fail）すること。



### 4. X.509 証明書パスの検証（不正なTLS証明書の遮断）

* **Requirement:** **FIA_X509_EXT.1** (X.509 Validation of Certificates)
* **Google ST (Android 15) Context:**
  TSF（Conscrypt/BoringSSL）が通信確立時に証明書チェーン、有効期限、基本制約を検証し、無効な場合は接続を拒否するとされています。
* **具体的なテスト手順 (UI不要):**
1. HTTPS通信を行うテストアプリをインストールする。
2. `adb shell am broadcast` 等を用いて、テストアプリに意図的に不正な証明書を持つエンドポイント（例: `https://expired.badssl.com/` や `https://self-signed.badssl.com/`）へリクエストを送信させる。


* **Logcatでの合否判定:**
* **Pass:** TLSハンドシェイクがOSの検証レイヤーで失敗し、Logcatに `javax.net.ssl.SSLHandshakeException` または `java.security.cert.CertPathValidatorException` が出力され、HTTPのレスポンスコード（200 OK等）が取得されないこと。



### 5. ドメイン分離によるシステム領域の改ざん防止検証

* **Requirement:** **FPT_AEX_EXT.4.1** (Domain Isolation)
* **Google ST (Android 15) Context:**
  STに基づき、Androidシステム領域（`/system`, `/vendor`等）は `dm-verity`（ブロックレベルの整合性保護）によって読み取り専用（Read-only）でマウントされており、特権を持たないアプリからの書き込みは厳密に隔離されます。
* **具体的なテスト手順 (UI不要):**
1. ファイル書き込みを試行するテストアプリをインストールする。
2. ADB経由でインテントを送信し、アプリに `/system/usr/test.txt` などのシステムディレクトリへのファイル作成（`File.createNewFile()`）を試行させる。


* **Logcatでの合否判定:**
* **Pass:** Logcatに `java.io.IOException: Read-only file system` または `Permission denied` の例外ログが出力され、ファイル書き込み完了のログが出力されないこと。



---

### Conclusion (結論)

これらのテスト手順は、UIAutomator等による画面操作の不安定さ（Flaky）を排除し、ADBによる「状態の注入（Input）」とLogcatによる「例外の監視（Output）」という純粋なプログラムの挙動のみでMDF PP V3.3の要件準拠（Satisfies）を証明可能です。

GoogleのAndroid 15実装（ST）に裏付けられたアーキテクチャの振る舞いを直接確認するため、CI/CDパイプライン上で安定して実行できる優れた自動テストケースとなります。