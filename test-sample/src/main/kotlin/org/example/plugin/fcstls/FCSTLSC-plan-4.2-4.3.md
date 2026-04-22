# Work Plan: Resolving Concerns 4.2 and 4.3 (FCS_TLSC_EXT)

Agent : Antigravity (AI Assistant) & User
Date  : 2026/04/22

Companion document to [FCSTLSC-note.md](./FCSTLSC-note.md). Addresses:
- **4.2** Renegotiation Test Strictness Limitation (isolate `renegotiation_info` as the sole rejection cause)
- **4.3** Identifier Verification Type Limitation (cover `IPAddress` / `URI` SAN types)

Shared premise: both are handled locally by introducing a **Custom Test Root CA** and installing it into the TOE. No publicly-trusted certificate is required.

---

## Phase 0: Shared Foundation — Custom Test Root CA & Local TLS Server

### 0.1 Certificate generation scripts
- New: `test-sample/scripts/tls/gen-test-ca.sh` — generates Root CA
- New: `test-sample/scripts/tls/gen-leaf-certs.sh` — generates leaf certs signed by the Root CA
- Output directory: `test-sample/src/main/resources/tls/`
- Generated artifacts:
  | File | Purpose |
  |---|---|
  | `test-root-ca.crt` / `test-root-ca.key` | Root CA (installed into TOE trust store) |
  | `leaf-localhost-dns.{crt,key,p12}` | SAN: `DNS:localhost` — used by 4.2 + dNSName regression |
  | `leaf-ip-127.0.0.1.{crt,key,p12}` | SAN: `IP:127.0.0.1` — 4.3 IPAddress positive |
  | `leaf-ip-wrong.{crt,key,p12}` | SAN: `IP:10.0.0.99` — 4.3 IPAddress negative |
  | `leaf-uri.{crt,key,p12}` | SAN: `URI:urn:example:tls-test` — 4.3 URI positive (scope caveat, see 2.2) |
- PKCS12 bundles included so `LocalTlsServer` can load keys/certs via `KeyStore.getInstance("PKCS12")` without additional PEM parsing.

### 0.2 CA installation helper
- New: `common-utils/src/main/kotlin/org/example/plugin/utils/TestCaInstaller.kt`
  - `installUserCa(client, serial, caFile)` — pushes `test-root-ca.crt` into Android user trust store
  - `removeUserCa(client, serial)` — teardown cleanup
- `openurl` app: add a **test** build variant with `network_security_config.xml` that trusts user CAs (`<certificates src="user"/>`). Production variant remains unchanged.

### 0.3 Local TLS server (SSLServerSocket-based)
- New: `test-sample/src/main/kotlin/org/example/plugin/fcstls/mock/LocalTlsServer.kt`
- Loads a PKCS12 leaf via `SSLContext` + `KeyManagerFactory`, accepts TLS 1.2/1.3, returns `HTTP/1.1 200 OK` on any request
- Constructor takes a `CertProfile` enum so the same server can serve different leaves across tests
- Exposes a `ServerHelloInterceptor` hook used by Phase 1 to strip extensions

---

## Phase 1: 4.2 — Strict Renegotiation Test

**Goal:** Reject with alert 47 where the **only** handshake defect is the missing `renegotiation_info` extension.

### 1.1 Build a valid-but-extension-stripped server
Two approaches, **recommended: A (Netty)**:

- **Option A (recommended): Netty + `SSLEngine` hook**
  - New: `test-sample/.../fcstls/mock/Tls12NoRenegInfoServer.kt`
  - Use Netty's `SslHandler` with a custom `SSLEngine` wrapper that intercepts `ServerHello` and strips the `renegotiation_info` extension bytes
  - Leaf: `leaf-localhost-dns.p12`
  - Pros: Full valid ECDHE_RSA handshake (proper ServerKeyExchange signature), low effort
  - Cons: adds Netty dependency to `test-sample`

- **Option B: Extend the existing raw-socket mock**
  - Reuse the structure of [Tls12InsecureMockServer.kt](./mock/Tls12InsecureMockServer.kt)
  - Add: manual ECDHE key agreement + signature generation using `leaf-localhost-dns.key`, full ServerKeyExchange record, CertificateRequest optional
  - Pros: no new dependencies, stays consistent with other mocks
  - Cons: heavier implementation (ECDHE signature construction by hand)

### 1.2 Rewrite the test
- [FcsTlscExt4RenegotiationTest.kt](./FcsTlscExt4RenegotiationTest.kt)
  - `@Before`: `TestCaInstaller.installUserCa(...)` + `adb reverse` (unchanged)
  - Replace `Tls12InsecureMockServer` with the new Phase 1.1 server
  - Assertions:
    - HTTP response is NOT `200`
    - Mock observes `FATAL_ALERT` with `desc == 47` (`illegal_parameter`)
    - logcat does NOT contain `WRONG_CERTIFICATE_TYPE`
    - logcat contains `RENEGOTIATION` or `renegotiation_info` related tokens (BoringSSL error string grep)

### 1.3 Note update
- Update [FCSTLSC-note.md](./FCSTLSC-note.md) section 4.2 → resolved, referencing Custom CA flow.

---

## Phase 2: 4.3 — Identifier Verification Name Types (FCS_TLSC_EXT.1.5)

**Goal:** Cover every name type claimed by the TOE's Security Target under the `FCS_TLSC_EXT.1.5` selection, at the granularity the evaluation target scope allows.

### 2.0 Background: the ST-claim vs. API-reachability asymmetry

`FCS_TLSC_EXT.1.5` in PKG_TLS_V2.1 ([FCSTLSC-android-spec.md:160-179](./FCSTLSC-android-spec.md#L160-L179)) is a two-level selection. The outer selection picks how the TSF performs identifier matching; if the "verify that a presented identifier of name type [selection]" branch is claimed, the inner selection lists which name types are in scope. Possible inner types (with plain-language description):

- `dNSName` (RFC 6125) — DNS hostname, e.g. `www.example.com`
- `uniformResourceIdentifier` (RFC 6125) — URI, e.g. `sip:alice@example.com`, `urn:...`
- `SRVname` (RFC 6125) — service locator, e.g. `_xmpp-client.example.com`
- `Common Name conversion to dNSName` (RFC 5280 + RFC 6125) — legacy CN-as-hostname fallback when no SAN is present
- `directoryName` (RFC 5280) — X.500 Distinguished Name, e.g. `C=US,O=Example,CN=...`
- `IPAddress` (RFC 5280) — literal IPv4/IPv6 address
- `rfc822Name` (RFC 5280) — **email address** in RFC 822 form (e.g. `alice@example.com`); typical for S/MIME and EAP-TLS client certs, not server certs

**Observed state (as of 2026-04-22):** the Security Target applicable to this TOE appears to claim the full (or near-full) inner set, *including* `uniformResourceIdentifier`, `SRVname`, `directoryName`, `rfc822Name`.

**The asymmetry:** the evaluation target scope in [FCSTLSC-note.md §1.3](./FCSTLSC-note.md) is "Standard internet clients (e.g., Chrome/OkHttp3) targeting HttpURLConnection, OkHttp3 APIs." Those APIs only compare reference identifiers against `dNSName` and `IPAddress`. The authoritative basis for that limitation is **RFC 2818 §3.1 "Server Identity"** (the HTTPS-specific spec, 2000), which mandates `dNSName` SAN with `iPAddress` SAN for literal-IP URIs and allows `CN` only as a legacy fallback. RFC 6125 (2011) is a general framework and per its own §1.4 does *not* override RFC 2818 for HTTPS; its role here is only to refine `dNSName` wildcard-matching rules (§6.4.3). RFC 6125 §6.5 (SRV-ID) and §6.5.2 (URI-ID) explicitly require a per-application-protocol opt-in, and **HTTPS has never adopted them**. Consequently no Android-shipped standard HTTPS client API consults URI / SRVname / rfc822Name / directoryName. Reaching those name types in a TLS client context requires either:

- a different TSF entry point (EAP-TLS supplicant via `wpa_supplicant`, IPsec/IKE daemons, enterprise VPN stacks), or
- a custom application that directly invokes `X509TrustManager` / `Conscrypt` internals and implements its own identifier matching.

Either way, the resulting deployment is narrow — typically enterprise / intranet / managed-device scenarios. General-purpose app developers using the standard HTTPS APIs cannot exercise these name types.

**Possible reasons the ST claims them anyway (kept here for future auditing):**

1. The underlying TSF (Conscrypt / BoringSSL / `HostnameChecker`) is *capable* of matching every name type in `libcore`'s validator, even though no public API wires all of them through. Claiming the capability of the TSF (not the API) is defensible if backed by design documentation.
2. Third-party or OEM-bundled clients (device management agents, enterprise SDKs, carrier apps) may use non-public interfaces that do reach these name types, and the vendor wants the ST to cover them.
3. Template inheritance — the ST was drafted from a PKG_TLS_V2.1 template and the selection was kept broad without a tight mapping to evaluated interfaces. (Common in practice; not ideal but not disqualifying.)

Any of these is plausible, and without vendor ATE / design docs in hand, we treat the claim as authoritative and the testing gap as something to **document, not argue with**. If a "super client" implementation surfaces later that actually uses URI/SRV/etc., this section is where to cross-reference it.

### 2.1 Runtime test matrix (what this testbed covers)

Runtime coverage in this testbed is bounded by what the `openurl` HttpURLConnection/OkHttp3 driver can reach. That is the intentional scope — broader name types are handled per §2.2.

| Name type | Runtime test? | Leaf cert | Driver | Positive test | Negative test |
|---|---|---|---|---|---|
| `dNSName` | **Yes** | `leaf-localhost-dns` | HttpURLConnection / OkHttp3 via `openurl` APK | `https://localhost:<port>/` → 200 | `https://wrong.example:<port>/` → fail |
| `IPAddress` | **Yes** | `leaf-ip-127.0.0.1` | HttpURLConnection / OkHttp3 | `https://127.0.0.1:<port>/` → 200 | `leaf-ip-wrong` served at `127.0.0.1` → fail |
| `CN → dNSName` | Optional | leaf with only CN=`localhost`, no SAN | HttpURLConnection / OkHttp3 | Behavior per ST (modern Android rejects CN fallback) | Not claimed by most STs; skip unless claimed |
| `uniformResourceIdentifier` | **No** — §2.2 fallback | `leaf-uri` (generated by Phase 0.1 scripts for future use) | No HttpURLConnection path | — | — |
| `SRVname` | **No** — §2.2 fallback | (not generated) | No HttpURLConnection path | — | — |
| `directoryName` | **No** — §2.2 fallback | (not generated) | No TLS-client path on Android | — | — |
| `rfc822Name` | **No** — §2.2 fallback | (not generated) | No HttpURLConnection path | — | — |

### 2.2 Unreachable name types: evidence strategy

For every inner-selection item that the ST claims but this testbed cannot drive at runtime:

- **Primary evidence:** vendor ATE results + TSF design documentation (per CEM). Expected content: pointer to the Android `libcore` / Conscrypt code path that performs the match, plus any vendor-internal test results.
- **Secondary supporting evidence (where we can contribute):** AOSP source references. Candidates worth citing in note §4.3 when finalizing:
  - `external/conscrypt/repackaged/common/src/main/java/com/android/org/conscrypt/OkHostnameVerifier.java` — concrete proof of the dNSName + iPAddress scope: `verifyHostname(String, X509Certificate)` walks only SAN type 2 (dNSName), and `verifyIpAddress(String, X509Certificate)` walks only SAN type 7 (iPAddress). URI (type 6), rfc822Name (type 1), directoryName (type 4) are not inspected.
  - `external/conscrypt/repackaged/common/src/main/java/com/android/org/conscrypt/` — TrustManager / X.509 chain validation
  - `libcore/ojluni/src/main/java/sun/security/util/HostnameChecker.java` — legacy JSSE reference matcher (dNSName / iPAddress / CN fallback)
  - `frameworks/opt/net/wifi/` (wpa_supplicant integration) — EAP-TLS identifier matching path for URI/SRV/rfc822
  - OkHttp3 upstream: [`okhttp/src/main/kotlin/okhttp3/internal/tls/OkHostnameVerifier.kt`](https://github.com/square/okhttp/blob/master/okhttp/src/main/kotlin/okhttp3/internal/tls/OkHostnameVerifier.kt) — the app-layer HTTPS client used by most modern Android apps; same dNSName + iPAddress scope as Conscrypt's verifier
- **Normative basis for the API-surface limitation:**
  - **RFC 2818 §3.1 "Server Identity"** — HTTPS-specific spec that restricts identity to `dNSName` SAN + `iPAddress` SAN + `CN` legacy fallback. This is the authoritative reason HttpURLConnection / OkHttp3 do not look at URI / SRV / rfc822Name / directoryName.
  - **RFC 6125 §1.4** — explicitly states it does not supersede application-specific specs like RFC 2818. Its role for HTTPS is limited to clarifying dNSName wildcard rules (§6.4.3).
  - **RFC 6125 §6.5 / §6.5.2** — SRV-ID and URI-ID matching require per-protocol opt-in, which HTTPS has never declared.
- **Scope statement:** the test suite here does not replicate vendor ATE. It complements it by covering the two name types reachable through standard HTTPS client APIs, which are also the two mandated by RFC 2818 for HTTPS and the two most commonly used in the wild.

This is a **documented gap, not an omission.** If a reviewer asks why URI SAN is not exercised here, the answer is "evaluation target §1.3 is standard internet clients; URI SAN requires a non-standard TSF entry point; evidence for that path is supplied by vendor ATE / design docs, cross-referenced in §4.3."

### 2.3 Future re-scoping trigger

Conditions that would change the plan:

- **A public Android API surfaces that invokes URI/SRV/rfc822/directoryName matching.** If this happens in a future Android release, expand runtime tests to cover it and revise note §1.3 accordingly.
- **A device-management / enterprise SDK within evaluation scope uses those name types.** Add a dedicated driver (EAP-TLS + RADIUS, IKE daemon, etc.) as a follow-up phase.
- **ST revision at next major Android version.** If the vendor chooses to narrow the 1.5 inner selection at the next ST revision (aligning claim with evaluation target scope), most of §2.2 becomes unnecessary. If the vendor chooses to keep the broad claim, this plan stays as-is.

### 2.4 Reuse Phase 0 server

- `LocalTlsServer(CertProfile.DNS_LOCALHOST)` / `LocalTlsServer(CertProfile.IP_127_0_0_1)` / `LocalTlsServer(CertProfile.IP_WRONG)`
- `@Before`/`@After` follow the same CA install + `adb reverse` pattern as [FcsTlscExt4RenegotiationTest.kt](./FcsTlscExt4RenegotiationTest.kt).
- The `leaf-uri.p12` generated by Phase 0.1 is **provisioned but unused at runtime** — kept so that if §2.3's first trigger fires, the cert is already in place.

### 2.5 Note update

Update [FCSTLSC-note.md](./FCSTLSC-note.md) section 4.3 to record:

1. The exact `FCS_TLSC_EXT.1.5` inner selection claimed by the ST applicable to this TOE.
2. The two name types (`dNSName`, `IPAddress`) covered by runtime tests in this testbed.
3. For every claimed name type not covered at runtime: cross-reference to the primary evidence (vendor ATE / design docs) and, where known, the AOSP source path that performs the match.
4. Explicit statement that the gap is **scope-driven** (evaluation target = standard internet clients) and **not** a general-purpose API degradation.

Do **not** frame the unreachable name types as "out of scope" on API-behavior grounds alone — that wording invites evaluator pushback. Frame them as "covered by a different evidence channel within the same ST claim."

---

## Execution Order

1. **Phase 0.1** — cert generation scripts (half-day, validated on host with `openssl verify`)
2. **Phase 0.2** — `TestCaInstaller` + `openurl` app NSC test variant (half to one day)
3. **Phase 0.3** — `LocalTlsServer` + smoke test (half-day)
4. **Phase 2** first — identifier tests are straightforward and validate Phase 0 infrastructure end-to-end
5. **Phase 1** — renegotiation stripping server (heaviest; benefits from Phase 0 already verified)
6. Update [FCSTLSC-note.md](./FCSTLSC-note.md) once both phases pass

---

## Risks / Decisions to Confirm

- **Android 7+ user CA restriction**: apps must opt in via `network_security_config.xml`. Decide whether to add a **test build variant** to `openurl` or ship a separate test APK.
- **Non-rooted evaluation device**: system trust store is not writable → user CA + NSC route is mandatory. Confirm whether the evaluation target is rooted.
- **Netty dependency**: Option 1.1-A introduces a new dependency in `test-sample`. Option 1.1-B keeps dependencies lean at the cost of implementation complexity. Default to A unless dependency hygiene is a hard requirement.
- **IP mismatch test (`127.0.0.2`)**: requires a second `adb reverse` mapping and connecting by the alternate IP. Verify this works on the target device; fall back to a host-header/SNI-based mismatch test if not.
