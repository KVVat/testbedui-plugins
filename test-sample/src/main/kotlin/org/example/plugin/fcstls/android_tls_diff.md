# Summary of Differences: Android TLS Specifics vs. Baseline Requirements

**Date:** 2026-04-22  
**Context:** Analysis of `FCSTLSC-android-spec.md` against standard/minimum TLS Package requirements.

---

## Overview
This document summarizes the key differences (deltas) between the average/minimum requirements typically found in the Functional Package for TLS and the specific claims Android is making in `FCSTLSC-android-spec.md`.

## Key Deltas

### 1. CNSA (Commercial National Security Algorithm) Compliance
*   **Baseline:** Usually requires standard, widely-used ciphersuites (e.g., TLS 1.2 with AES-128-GCM) and curves (e.g., secp256r1).
*   **Android Spec:** Explicitly claims support for **CNSA 1.0 and 2.0** compliant suites and signature algorithms.
    *   *Ciphersuites:* Includes high-security suites like `TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384` and `TLS_AES_256_GCM_SHA384`.
    *   *Signatures & Groups:* Requires `ecdsa_secp384r1_sha384` and the `secp384r1` group.
*   **Test Impact:** We should verify that these high-strength parameters are actively offered in the `ClientHello`.

### 2. Supplemental Downgrade Protection (FCS_TLSC_EXT.3.1)
*   **Baseline:** Often considers downgrade protection via Server Random signals as conditional or optional unless protocol fallback is explicitly claimed.
*   **Android Spec:** Explicitly claims this support. The TOE must abort the connection if it detects the TLS 1.3 -> 1.2 downgrade indicator in the `ServerRandom` field.
*   **Test Impact:** Already verified via our custom raw-socket mock server (`FcsTlscExt3SignalTest`).

### 3. Explicit Disabling of 0-RTT / Early Data (FCS_TLSC_EXT.6.2)
*   **Baseline:** Many profiles allow or remain silent on TLS 1.3 Early Data (0-RTT) as it is a standard performance feature.
*   **Android Spec:** Explicitly forbids sending `early_data`. The TSF must **not** send early data in TLS 1.3 sessions to avoid replay attack risks.
*   **Test Impact:** We must verify the **absence** of the `early_data` extension in the `ClientHello` during PCAP analysis.


