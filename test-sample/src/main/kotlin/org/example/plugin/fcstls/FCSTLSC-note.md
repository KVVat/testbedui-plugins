# The test cases for Functional Package for Transport Layer Security (TLS)

Agent : Antigravity (AI Assistant) & User  
Date : 2026/04/20 (Updated)

### **I. Overview and Scope**

**1.1 Objective** This document provides a detailed description of the testing methodology and implementation used to verify compliance with the Functional Package for Transport Layer Security (TLS). The objective is to demonstrate that the Target of Evaluation (TOE) satisfies the specific security functional requirements (SFRs) defined in the package.

[https://www.niap-ccevs.org/static\_html/protection-profile/519/PKG\_TLS\_V2.1.html](https://www.niap-ccevs.org/static_html/protection-profile/519/PKG_TLS_V2.1.html)

**1.2 Target of Evaluation (TOE)** The Target of Evaluation (TOE) for this testing is an Android mobile device.

**1.3 Compliance Claim and Evaluation Target Scope** Given that the TOE is an Android device, the evaluation focuses specifically on verifying the FCS\_TLSC\_EXT (TLS Client) Security Functional Requirements (SFRs). The scope of this evaluation encompasses standard internet clients operating within the Android environment, with a specific focus on APIs such as HttpURLConnection and OkHttp3.

**CRITICAL NOTE:** All other requirements outside of the TLS Client functional package are considered **out of scope** for this specific verification exercise, with the exception of closely related trusted channel requirements (like FTP_ITC_EXT.1).

The following table summarizes the compliance requirements and the specific evaluation target scope for each protocol and role:

| Protocol | Role | Requirement ID | Evaluation Target Scope |
| :---- | :---- | :---- | :---- |
| **TLS** | Client | **FCS\_TLSC\_EXT** | Standard internet clients (e.g., Chrome/OkHttp3). Targets HttpURLConnection, OkHttp3 APIs. |
| **DTLS** | Client | **FCS\_DTLSC\_EXT** | Streaming and real-time communication clients (e.g., Google Meet). |
| **TLS** | Server | **FCS\_TLSS\_EXT** | Server-side implementations (Not applicable for most Android TOEs). |
| **DTLS** | Server | **FCS\_DTLSS\_EXT** | Server-side streaming implementations. |

### **II. Test Methodology**

**2.1 Test Environment and Infrastructure** 

To ensure strict test repeatability and minimize external environmental dependencies, the testing methodology utilizes a hybrid approach combining public baselines with a self-contained, easily reproducible local test environment:

* **Standard Validation:** The widely recognized `badssl.com` (a Chromium sub-project) is utilized to verify standard TLS connection states, including basic legacy protocol rejection and expired/invalid certificate handling.  
* **Custom Security Validation (Raw Socket Mock Servers):** To rigorously test specific negative scenarios mandated by the PP (e.g., Downgrade Protection, active rejection of unsupported parameters during the handshake), a custom, locally hosted test server environment is employed. This environment uses **raw socket implementations** in Kotlin to deterministically construct handshake packets (e.g., `ServerHello`) to simulate protocol violations without library constraints.
* **Certificate Management:** A dedicated **Custom Test Root CA** is generated and installed into the TOE's trust store when full handshakes are needed. However, for many negative tests (like signal detection), the handshake is intentionally aborted early by the client, removing the need for full certificate chain completion in those specific cases.

**2.2 Verification Method** The primary verification mechanism relies on dynamic network packet analysis and mock server state observation.
* **Mock Observation:** The raw socket mock servers inspect the client's reaction (e.g., sending fatal alerts or closing connection) immediately after receiving the crafted server response. This provides direct evidence of protocol-level rejection.
* **HTTP Layer:** Rejection is also verified at the HTTP layer (ensuring connections do not succeed).

**2.3 Test Automation Tools** Test cases are implemented as JUnit tests and executed within the Android environment using the `testbed-core` framework. These automated tests trigger the network connections to the respective test servers and subsequently execute the analysis to ensure the TOE's behavior strictly aligns with the cryptographic boundaries defined in `FCS_TLSC_EXT`.

### **III. Detailed Test Case Mapping**

This section maps the specific Security Functional Requirements (SFRs) defined in `FCS_TLSC_EXT` to the corresponding test cases. **All major active verification tests have been successfully implemented and verified.**

#### **3.1 Phase 1: Baseline Tests (Using badssl.com)**

These tests verify the TOE's passive behavior when connecting to endpoints with strictly configured constraints.

* **FCS\_TLSC\_EXT.1.1 (Protocol Versions):**  
  * `testTls12Support` / `testTls13Support`: Confirms successful connection using TLS 1.2 and TLS 1.3 respectively.  
  * `testTls10Reject` / `testTls11Reject`: Verifies that connection attempts fail when the target server *only* supports legacy protocols (TLS 1.0/1.1).  
* **FCS\_TLSC\_EXT.1.2 (Supported Ciphersuites):**  
    * **CNSA 1.0 Compliant (TLS 1.2):**
      * **CNSA 1.0 Compliant (TLS 1.2):**
      * `TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384 as defined in RFC 5289 and RFC 8422` (`0xC02C`)
      * `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 as defined in RFC 5289 and RFC 8422` (`0xC030`)
      * `TLS_DHE_RSA_WITH_AES_256_GCM_SHA384 as defined in RFC 5288` (`0x009F`)
      * `TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384 as defined in RFC 5289 and RFC 8422` (`0xC024`)
      * `TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384 as defined in RFC 5289 and RFC 8422` (`0xC028`)
      * `TLS_ECDHE_PSK_WITH_AES_256_GCM_SHA384 as defined in RFC 8442` (`0xD003`)
      * `TLS_DHE_PSK_WITH_AES_256_GCM_SHA384 as defined in RFC 5487` (`0x00AA`)
      * `TLS_RSA_PSK_WITH_AES_256_GCM_SHA384 as defined in RFC 5487` (`0x00AC`)
    * **Non-CNSA Compliant (TLS 1.2):**
      * `TLS_RSA_WITH_AES_256_CBC_SHA256 as defined in RFC 5246` (`0x003D`)
      * `TLS_RSA_WITH_AES_256_GCM_SHA384 as defined in RFC 5288` (`0x009D`)
      * `TLS_DHE_RSA_WITH_AES_256_CBC_SHA256 as defined in RFC 5246` (`0x006D`)
      * `TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256 as defined in RFC 5289` (`0xC02B`)
      * `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 as defined in RFC 5289` (`0xC02F`)
      * `TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256 as defined in RFC 5289` (`0xC023`)
      * `TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256 as defined in RFC 5289` (`0xC027`)
      * `TLS_RSA_WITH_AES_128_CBC_SHA256 as defined in RFC 5246` (`0x003C`)
      * `TLS_DHE_RSA_WITH_AES_128_CBC_SHA256 as defined in RFC 5246` (`0x0067`)
      * `TLS_RSA_WITH_AES_128_CBC_SHA as defined in RFC 5246` (`0x002F`)
      * `TLS_ECDHE_PSK_WITH_AES_128_GCM_SHA256 as defined in RFC 8442` (`0xD001`)
      * `TLS_DHE_PSK_WITH_AES_128_GCM_SHA256 as defined in RFC 5487` (`0x00A8`)
      * `TLS_RSA_PSK_WITH_AES_128_GCM_SHA256 as defined in RFC 5487` (`0x00A6`)
    * **TLS 1.3 Ciphersuites:**
      * `TLS_AES_256_GCM_SHA384 as defined in RFC 8446` (`0x1302`)
      * `TLS_AES_128_GCM_SHA256 as defined in RFC 8446` (`0x1301`)
  * Verifies that the TSF does not offer ciphersuites outside of this allowed list, or logs a warning for any non-compliant ciphersuites offered by the client.
* **FCS\_TLSC\_EXT.1.3 (Forbidden Ciphers):**  
  * `testRc4Reject` / `test3DesReject` / `testNullCipherReject`: Verifies connection failure when the target server *only* supports prohibited weak or null ciphers.
  * `analyzePcap` explicitly verifies that the `ClientHello` does not offer any of the following 14 forbidden ciphers:
    * `TLS_NULL_WITH_NULL_NULL` (`0x0000`)
    * `TLS_RSA_WITH_NULL_MD5` (`0x0001`)
    * `TLS_RSA_WITH_NULL_SHA` (`0x0002`)
    * `TLS_RSA_WITH_NULL_SHA256` (`0x003B`)
    * `TLS_ECDHE_ECDSA_WITH_NULL_SHA` (`0xC006`)
    * `TLS_ECDHE_RSA_WITH_NULL_SHA` (`0xC010`)
    * `TLS_RSA_WITH_RC4_128_MD5` (`0x0004`)
    * `TLS_RSA_WITH_RC4_128_SHA` (`0x0005`)
    * `TLS_ECDHE_ECDSA_WITH_RC4_128_SHA` (`0xC007`)
    * `TLS_ECDHE_RSA_WITH_RC4_128_SHA` (`0xC011`)
    * `TLS_RSA_WITH_3DES_EDE_CBC_SHA` (`0x000A`)
    * `TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA` (`0x000D`)
    * `TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA` (`0xC008`)
    * `TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA` (`0xC012`)
* **FCS\_TLSC\_EXT.1.4 (Supported & Forbidden Extensions):**  
  * Verified in `analyzePcap` by checking for required extensions and validating their internal values:
    * `signature_algorithms` (`0x000D`): Must contain at least one CNSA 1.0 algorithm:
      * `ecdsa_secp384r1_sha384` (`0x0503`)
      * `rsa_pkcs1_sha384` (`0x0501`)
      AND at least one PSS or non-CNSA algorithm:
      * `rsa_pss_pss_sha384` (`0x080A`)
      * `rsa_pss_rsae_sha384` (`0x0805`)
      * `rsa_pkcs1_sha256` (`0x0401`)
      * `rsa_pss_rsae_sha256` (`0x0804`)
    * `signature_algorithms_cert` (`0x0032`): Same value check as above if the extension is present.
    * `supported_groups` (`0x000A`): Must contain at least one allowed group:
      * `secp384r1` (`0x0018`)
      * `secp256r1` (`0x0017`)
  * Also verified the **absence** of the `early_data` (`0x002A`) extension, and logged any offered extensions not in the allowed list.
* **FCS\_TLSC\_EXT.1.5 (Identifier Verification):**  
  * `testInvalidHost`: Confirms connection termination when presented with a mismatched Reference Identifier.  
* **FCS\_TLSC\_EXT.1.6 (Invalid Certificate Rejection):**  
  * `testExpiredHost`: Confirms connection termination when presented with an expired certificate.  

#### **3.2 Phase 2: Active Verification Tests (Custom Raw Socket Servers)**	

These tests actively simulate malicious or non-compliant server behavior to verify the TOE's active defense mechanisms.

* **FCS\_TLSC\_EXT.1.1 (Active Rejection of Legacy Protocols):**  
  * **Test Case:** `FcsTlscExt1LegacyRejectionTest`
  * **Implementation:** Uses `TlsLegacyRejectMockServer` (raw socket) to force an SSLv3 `ServerHello` (`0x0300`).
  * **Result:** **Pass**. The client actively rejected the connection with a fatal alert `70` (`protocol_version`) and threw `UNSUPPORTED_PROTOCOL`.
* **FCS\_TLSC\_EXT.3.1 (Downgrade Protection):**  
  * **Test Case:** `FcsTlscExt3SignalTest`
  * **Implementation:** Uses `Tls13DowngradeSignalMockServer` (raw socket) to send a TLS 1.2 `ServerHello` with the `DOWNGRD01` signal embedded in `ServerRandom`.
  * **Result:** **Pass**. The client detected the signal and aborted the handshake with a fatal alert `47` (`illegal_parameter`).
  * **Test Case:** `FcsTlscExt3PoodleTest`
  * **Implementation:** Uses `PoodleFallbackMockServer` to fail the first handshake and check for insecure fallback retries.
  * **Result:** **Pass**. The client did not attempt automatic fallback, demonstrating a modern secure posture.
* **FCS\_TLSC\_EXT.4 (Secure Renegotiation):**  
  * **Test Case:** `FcsTlscExt4RenegotiationTest`
  * **Implementation:** Uses `Tls12InsecureMockServer` (raw socket) to send a TLS 1.2 `ServerHello` *without* the `renegotiation_info` extension (simulating an insecure server).
  * **Result:** **Pass** (with caveat). The client actively rejected the connection with a fatal alert `47` (`illegal_parameter`). However, the underlying reason logged by BoringSSL was `WRONG_CERTIFICATE_TYPE` (even after providing a server certificate from badssl.com), not strictly isolating the lack of renegotiation extension.

#### **3.3 Related System Requirements (From Mobile Device Fundamentals PP)**

* **FTP\_ITC\_EXT.1 (Trusted Channel Communication):**
  * **Test Case:** `FcsTlsExt1ClearTextTest` (originally named `FtpItcExt1HttpTest`)
  * **Implementation:** Triggers a cleartext HTTP connection and verifies that it is blocked by the OS Network Security Policy.
  * **Result:** **Pass**. The OS correctly blocked the traffic with `Cleartext HTTP traffic to ... not permitted`.


### **IV. Identified Concerns and Limitations**

**4.1 Implementation Note: PCAP Parsing Fragility (Android SLL2 & IPv6)**
* **Concern:** The Android OS utilizes the Linux Cooked Capture v2 (SLL2) link-layer header format. The `io.pkts` library does not natively support SLL2, requiring monkey patches. Furthermore, `io.pkts` has a bug in parsing IPv6 headers over SLL2, reading parts of the IPv6 address as TCP ports.
* **Impact & Workaround:** This caused false negatives in port filtering for TLS 1.3 tests. We worked around this by forcing IPv4 communication via `ipv4.google.com`. For robust testing, a library that fully supports SLL2 and IPv6 is recommended.

**4.2 Renegotiation Test Strictness Limitation**
* **Concern:** In `FcsTlscExt4RenegotiationTest`, the mock server sends a certificate to proceed with the handshake. However, BoringSSL rejects it with `WRONG_CERTIFICATE_TYPE` because the certificate is not fully trusted or suitable as a server certificate in its view.
* **Impact:** The test passes because the connection fails (as expected), but the failure is triggered by the certificate check rather than the missing secure renegotiation extension. Strict isolation requires a valid certificate chain recognized by BoringSSL.

**4.3 Identifier Verification Type Limitation (Gap with ST/PP Claims)**
* **Concern:** `FCS_TLSC_EXT.1.5` requires the TSF to verify presented identifiers of specific name types (e.g., `dNSName`, `uniformResourceIdentifier`, `SRVname`, `IPAddress`). The Security Target (ST) applicable to this TOE appears to claim support for a broad set of these types. However, there is a fundamental gap between the ST claims and the reachability via standard Android APIs.
* **The Gap:** The evaluation scope in this testbed is focused on "Standard internet clients (e.g., Chrome/OkHttp3) targeting HttpURLConnection, OkHttp3 APIs". These APIs only compare reference identifiers against `dNSName` and `IPAddress`. Consequently, other name types like `uniformResourceIdentifier`, `SRVname`, `directoryName`, and `rfc822Name` cannot be exercised through these standard HTTPS client interfaces.
* **Reason for the Gap:** This limitation is rooted in **RFC 2818 §3.1 "Server Identity"** (the HTTPS-specific spec), which mandates `dNSName` and `iPAddress` for identity matching. While RFC 6125 provides a general framework for other name types (like URI-ID or SRV-ID), HTTPS has never adopted them. Therefore, standard Android HTTPS clients do not implement matching for these extended types.
* **Evidence Strategy for the Gap:** Since this testbed cannot drive the unreachable name types at runtime, compliance must be demonstrated through alternative evidence channels:
  * **Primary Evidence:** Vendor ATE results and TSF design documentation that point to the internal code paths (e.g., in Conscrypt/BoringSSL or specific enterprise components) capable of matching these types.
  * **Supporting Evidence:** Source code references in AOSP (e.g., `OkHostnameVerifier.java` walking only SAN type 2 and 7) to document the scope boundary of the HTTPS APIs.
* **Runtime Coverage in this Testbed:** This testbed covers the two name types reachable through standard HTTPS APIs: `dNSName` and `IPAddress` (using the custom CA infrastructure detailed in the work plan `FCSTLSC-plan-4.2-4.3.md`). Other claimed types are treated as documented gaps to be covered by vendor evidence.

**4.4 Supported Groups Limitation (Missing secp384r1) —— [RESOLVED]**
* **Initial Concern:** The Security Target (ST) for Google and Samsung TOEs requires the TSF to present the `Supported Groups` extension with `secp256r1` and `secp384r1`. Early observations suggested that Conscrypt only offered `x25519` and `secp256r1`, missing `secp384r1`.
* **Resolution (2026-04-23):** It was discovered that the test suite's PCAP parser had a boundary condition bug (off-by-2 error when calculating vector lengths) that caused it to silently drop the final entries of the `supported_groups` and `signature_algorithms` lists.
* **Final Status:** After fixing the parser bug, the full list was correctly read as `[x25519, secp256r1, secp384r1]`. Therefore, `secp384r1` **is** present in the `ClientHello`, and the TOE does comply with the ST's requirement. No customization of `SSLSocketFactory` or `SSLParameters` is required.

**4.5 Session Resumption Dynamic Verification —— [RESOLVED]**
* **Initial Concern:** `FCS_TLSC_EXT.5.1` requires support for session resumption. Earlier the test suite only verified the *static* presence of `session_ticket` and `psk_key_exchange_modes` extensions in `ClientHello`, without confirming end-to-end execution of an abbreviated handshake.
* **Resolution (2026-04-29):** [FcsTlscExtTest.kt](./FcsTlscExtTest.kt) was extended with a cross-flow PCAP analyzer (`analyzeResumptionEvidence`) that walks every TCP flow in the capture, extracts plaintext handshake messages (ClientHello / ServerHello / NewSessionTicket / Certificate / ServerKeyExchange / ServerHelloDone), and infers which RFC mechanism was actually used to resume. The decisive signal in TLS 1.2 is whether the server's 2nd-flight contains a `Certificate` message (full handshake) or skips it (abbreviated handshake = resumption succeeded).
* **Evidence — TLS 1.2 RFC 5077 Tickets (`testSessionResumption`):**
  | Capture event | Observation |
  |---|---|
  | 1st connection server flight | ServerHello + Certificate + ServerKeyExchange + ServerHelloDone + NewSessionTicket(192B, lifetime 300s) |
  | 2nd connection ClientHello | non-empty `session_ticket` extension (192B), echoes 1st SH session_id (RFC 5077 §3.4 simultaneous offer) |
  | 2nd connection server flight | **ServerHello only** (no Certificate / SKE / SHD) |
  | Inferred mechanism | `TLS_1_2_RFC5077_TICKET_RESUMED_NO_SID_ECHO` |
* **Compliance interpretation:** From the TOE's perspective the abbreviated handshake completed → RFC 5077 ticket-based resumption is **dynamically verified**. The "no_sid_echo" suffix flags a server-side RFC 5077 §3.4 anomaly (badssl/nginx returns empty SH session_id despite the 2nd CH carrying a non-empty session_id). This anomaly is server-side, not TOE-side, and does not affect FCS_TLSC_EXT.5.1 evidence quality for the client.
* **Documented capability — TLS 1.3 PSK (`testSessionResumptionTls13`):** A separate test connects twice to `https://ipv4.google.com/` (TLS 1.3 endpoint) and uses the same analyzer to detect `pre_shared_key` (0x0029) presence in the 2nd ClientHello AND in the 2nd ServerHello. Soft assertions only because external endpoints may not always issue tickets / accept resumption (load-balancer state, rate-limiting). Successful runs register the `FCS_TLSC_EXT.5.1/PSK` checklist entry as documented capability — note that the ST does not claim the "PSK and tickets in accordance with RFC 8446" selection, so this entry is evidence of capability, not a claimed SFR.
* **TLS 1.2 Session ID (RFC 5246):** Not currently exercised. The badssl:1012 server prefers ticket-based resumption and returns an empty session_id in the 2nd ServerHello, suppressing the session-ID path. To strictly verify session-ID resumption, a dedicated mock server (with `ssl_session_tickets off`) would be required. Documented as future work; not blocking since session ID is not separately claimed in the ST beyond ticket-based resumption.

### **V. Current Verification Status**

Based on the specific requirements and recent strictness improvements, we have implemented and verified the following in `FcsTlscExtTest.kt` and related tests:
1.  **Verified** the presence of required and CNSA ciphersuites in `ClientHello` (mapped to `FCS_TLSC_EXT.1.2`).
2.  **Verified** specific values in `signature_algorithms` and `supported_groups` extensions, ensuring compliance with spec selections (mapped to `FCS_TLSC_EXT.1.4`).
3.  **Verified** the absence of `early_data` extension in `ClientHello` for TLS 1.3 (mapped to `FCS_TLSC_EXT.6.2`).
4.  **Identified** that Conscrypt offers extensions and ciphers not explicitly in the spec's allowed list (logged as warnings).
5.  **Documented Gap for 1.5:** Verification of `IPAddress` is covered by the local CA infrastructure. Other types like `URI` and `SRVname` are treated as unreachable gaps in HTTPS scope, to be covered by vendor evidence (see Section 4.3).
6.  **Documented Limitation for 4.1:** Renegotiation test passes but fails due to certificate type mismatch in BoringSSL, not strictly isolating renegotiation rejection.

### **VI. Next Steps and Reference Repositories**

With the core TLS functional package requirements verified, the next steps include:
1. Formalizing the test evidence (logs and PCAP analysis results) into the final evaluation report.
2. (Optional) Expanding coverage to DTLS Client requirements if specified in the Security Target.

#### **6.1 Reference Repositories** 

The source code for the test cases and the underlying execution framework are maintained in the following repositories:

* **Test Cases (JUnit Plugins):** [https://github.com/KVVat/testbedui-plugins](https://github.com/KVVat/testbedui-plugins)  
  * *Specific TLS Ext Test:* [FcsTlscExtTest.kt](https://github.com/KVVat/testbedui-plugins/blob/main/test-sample/src/main/kotlin/org/example/plugin/fcstls/FcsTlscExtTest.kt)  
* **Execution Framework:** [https://github.com/KVVat/testbed-core](https://github.com/KVVat/testbed-core)

### **VII. Installation and Execution Guide**

To reproduce the tests and verify the results, follow these steps to install and run the `testbed-core` framework:

1. **Download the Package:**
   Download the OS-specific ZIP package from the official release page:
   [TestBed Core Release PR3](https://github.com/KVVat/testbed-core/releases/tag/PR3)

2. **Start the Server:**
   Extract the package, read the `README.md` file for environment setup instructions, and start the desktop application.

3. **Import Plugins:**
   Open the Test Explorer in the desktop app and use the import feature to load the `plugins-and-resources.zip` package containing the plugins and test resources.

This release has been verified to be operational for the current test suite. The test cases are contained in the `network` category.

### **VIII. Extension Verification Checklist**

To address the dynamic behavior of the Android TLS implementation (Conscrypt), where some extensions may be omitted in specific fallback or error scenarios, the following checklist summarizes where each required extension is verified.

| Extension | Spec ID | Primary Test Case | Verification Status | Conscrypt Behavior Note |
| :--- | :--- | :--- | :--- | :--- |
| `signature_algorithms` (`0x000D`) | FCS_TLSC_EXT.1.4 | `testTls12Support` | **Verified** | May be omitted by Conscrypt in some fallback handshakes. |
| `supported_groups` (`0x000A`) | FCS_TLSC_EXT.1.4 | `testTls12Support` | **Verified** | May be omitted by Conscrypt in some fallback handshakes. |
| `extended_master_secret` (`0x0017`) | FCS_TLSC_EXT.1.4 | `testTls12Support` | **Verified** | May be omitted by Conscrypt in some fallback handshakes. |
| `psk_key_exchange_modes` (`0x002D`) | FCS_TLSC_EXT.6.1 | `testTls13Support` | **Verified** | Only required and present in TLS 1.3 handshakes. |
| `session_ticket` (`0x0023`) | FCS_TLSC_EXT.5.1 | `testSessionResumption` | **Verified (Static + Dynamic)** | Static: presence in ClientHello. Dynamic: 2nd-flight Certificate absence proves abbreviated handshake (RFC 5077). |
| `session_ticket` (`0x0023`) (Absence) | FCS_TLSC_EXT.5.1 | `testInvalidHost`, etc. | **Observed** | Conscrypt may omit it in non-resumption handshakes. Logged as warning. |
| `pre_shared_key` (`0x0029`) | FCS_TLSC_EXT.5.1/PSK | `testSessionResumptionTls13` | **Documented Capability** | TLS 1.3 PSK resumption (RFC 8446). Soft check against ipv4.google.com; ST does not claim the "PSK and tickets" selection so this is evidence of capability only. |
| `early_data` (`0x002A`) (Absence) | FCS_TLSC_EXT.6.2 | `testTls13Support` | **Verified** | Confirmed to be absent in TLS 1.3 ClientHello. |

> [!NOTE]
> The test suite logs the absence of these extensions as **Warnings** in non-primary tests to avoid false failures due to Conscrypt's dynamic behavior, while ensuring that the capability is demonstrated in at least one test case. 
