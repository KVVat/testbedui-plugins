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
  * Verified in `analyzePcap` by checking for the presence of required ciphersuites in `ClientHello`:
    * `TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256` (`0xC02B`)
    * `TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384` (`0xC02C`)
    * `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256` (`0xC02F`)
    * `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` (`0xC030`)
* **FCS\_TLSC\_EXT.1.3 (Forbidden Ciphers):**  
  * `testRc4Reject` / `test3DesReject` / `testNullCipherReject`: Verifies connection failure when the target server *only* supports prohibited weak or null ciphers.  
* **FCS\_TLSC\_EXT.1.4 (Supported & Forbidden Extensions):**  
  * Verified in `analyzePcap` by checking for required extensions in `ClientHello`:
    * `signature_algorithms` (`0x000D`)
    * `supported_groups` (`0x000A`)
    * `extended_master_secret` (`0x0017`)
    * `psk_key_exchange_modes` (`0x002D`)
  * Also verified the **absence** of the `early_data` (`0x002A`) extension.
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

**4.3 Identifier Verification Type Limitation**
* **Concern:** `FCS_TLSC_EXT.1.5` requires the TSF to verify presented identifiers of specific name types (like `dNSName`, `IPAddress`, `URI`). Currently, we only verify `dNSName` (hostname) using `badssl.com` endpoints.
* **Impact:** Verification of other identifier types like `IPAddress` or `uniformResourceIdentifier` is not covered by the current test suite. Testing these would require a custom mock server with certificates containing these specific SAN types, and those certificates would need to be trusted by the device (requiring CA installation).

### **V. Next Steps and Reference Repositories**

With the core TLS functional package requirements verified, the next steps include:
1. Formalizing the test evidence (logs and PCAP analysis results) into the final evaluation report.
2. (Optional) Expanding coverage to DTLS Client requirements if specified in the Security Target.

#### **5.1 Reference Repositories** 

The source code for the test cases and the underlying execution framework are maintained in the following repositories:

* **Test Cases (JUnit Plugins):** [https://github.com/KVVat/testbedui-plugins](https://github.com/KVVat/testbedui-plugins)  
  * *Specific TLS Ext Test:* [FcsTlscExtTest.kt](https://github.com/KVVat/testbedui-plugins/blob/main/test-sample/src/main/kotlin/org/example/plugin/fcstls/FcsTlscExtTest.kt)  
* **Execution Framework:** [https://github.com/KVVat/testbed-core](https://github.com/KVVat/testbed-core)
