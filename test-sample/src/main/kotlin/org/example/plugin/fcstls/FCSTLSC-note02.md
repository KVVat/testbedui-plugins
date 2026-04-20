# DowngradeMockServer 検討メモ（v2）

## 1. 現状実装の誤り / 修正ポイント（アウトライン）

### 1-1. 攻撃モデルの混線
- POODLE と RFC 8446 §4.1.3 downgrade signal は**別の攻撃/防御**。混ぜずに分ける。
  - **POODLE 系**（SSLv3/TLS1.0 fallback 誘発）の検証対象 → retry ClientHello に `TLS_FALLBACK_SCSV (0x5600)` が載るか。
  - **§4.1.3 signal** の検証対象 → TLS 1.2 ServerHello.random 末尾 8 バイト `44 4F 57 4E 47 52 44 01` を client が検出して abort するか。
- FCS_TLSC_EXT.3.1 は両方を含み得るが、**テストケースを 2 つに分割**するのが正。

### 1-2. "Resumption" 前提が誤り
- §4.1.3 signal は **初回 handshake で TLS 1.3→1.2 にネゴった瞬間**に server が入れる挙動。resumption とは無関係。
- `connectionCount` で 1回目=TLS1.3 / 2回目=downgrade と出し分ける設計は RFC と合わない。**単一接続で完結**させる。

### 1-3. BouncyCastle API の誤用
- [DowngradeMockServer.kt:92](mock/DowngradeMockServer.kt#L92) — `DefaultTlsServer` は `getCredentials()` を override していないため "credentials unhandled" で failed になる。P12 を読んでいるだけで server 側に渡っていない。
- [DowngradeMockServer.kt:61-76](mock/DowngradeMockServer.kt#L61-L76) — `SecureRandom.nextBytes` を `bytes.size == 32` でフックする方式は脆い:
  - BC 内部で 32 バイト要求する箇所は ServerHello.random 以外にも複数（鍵派生・IV 生成・session id 生成等）。意図しない書き換えが起こる。
  - `BcTlsCrypto(customRandom)` 経路が ServerHello.random 生成で必ず呼ばれる保証がない（BC のバージョンによってはキャッシュや別 RNG を使う）。

### 1-4. スレッドセーフティ
- [DowngradeMockServer.kt:31](mock/DowngradeMockServer.kt#L31) — `connectionCount` が複数ハンドラから非同期更新されるが同期なし。`@Volatile` や `AtomicInteger` すらない。

### 1-5. 判定レイヤの誤り
- [FcsTlscExt3DowngradeTest.kt:104-108](FcsTlscExt3DowngradeTest.kt#L104-L108) — HTTP ステータスで判定している。downgrade 検知は TLS レイヤの `alert` record が唯一の確実な証跡で、HTTP 層では "connection refused" 等に丸まるため**証拠として弱い**。

---

## 2. 推奨アーキテクチャ

### 2-1. テスト分離
| SFR 観点 | Mock | 期待挙動 |
|---|---|---|
| §4.1.3 signal 検知 | `Tls13DowngradeSignalMockServer` — TLS1.2 ServerHello + 末尾 DOWNGRD01 を送出 | client が `illegal_parameter(0x2F)` fatal alert で切断 |
| TLS_FALLBACK_SCSV 生成 | `FallbackScsvMockServer`（別途） — 初回を `internal_error` 等で拒否 | retry ClientHello の cipher_suites に `0x5600` が含まれる |

### 2-2. 実装方式 — raw socket 手組みを採用
- ServerHello.random の末尾 8 バイトを **決定的に** 制御するには、BC の抽象化に依存しない raw 構築が確実。
- 検証対象は ServerHello 受信時点の client 挙動のみ → Certificate / KeyExchange 以降は**不要**。
- PCAP 証拠が RFC と 1:1 対応し、CC 評価者による再現が容易。

### 2-3. 成否判定
- **PCAP 側**: client → server 方向の Alert record（type=0x15, level=fatal=0x02, desc=illegal_parameter=0x2F）の存在をチェック。
- **Mock 側**: ServerHello 送信後に client の後続 byte を観測し、alert / handshake 継続 / FIN のいずれかを log。
- HTTP レベルの失敗判定は補助のみ。

---

## 3. 次の作業
- [x] raw socket 版 Mock を [Tls13DowngradeSignalMockServer.kt](mock/Tls13DowngradeSignalMockServer.kt) として別ファイルで作成（現 `DowngradeMockServer.kt` は残したまま）。
- [ ] `FcsTlscExt3DowngradeTest` を単一接続で new Mock を使うように改修。
- [ ] PCAP 解析に Alert record 検出の assertion を追加。
- [ ] `FallbackScsvMockServer`（SCSV 観点）を別途起こす。
