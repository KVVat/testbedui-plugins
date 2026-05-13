# FIA_X509_EXT.1.2 Extensions Verification Results

This document summarizes the results of the exhaustive verification of X.509 certificate extensions listed in `FIA_X509_EXT.1.2`, focusing on how Conscrypt (the Android security provider) handles invalid or incorrect values.

## Summary of Results

| Extension | Test Case | Value Used | Critical? | Conscrypt Behavior | Result |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Basic Constraints** | `wrong_basic_constraints` | `CA:TRUE` (for end-entity) | Yes | **Rejected** (HTTP 525) | Pass |
| **Certificate Policies** | `wrong_policies` | Invalid OID | Yes | **Rejected** (HTTP 525) | Pass |
| **Extended Key Usage** | `wrong_eku` | `emailProtection` only (missing `serverAuth`) | No | **Rejected** (HTTP 525) | Pass |
| **Subject Alternative Name** | `wrong_san` | Mismatching DNS name | No | **Rejected** (HTTP 525) | Pass |
| | `san_dns_only` (connecting to IP) | `DNS:localhost` | No | **Accepted** (HTTP 200) | **Fail** (Unexpected Success) |
| | `san_ip_only` (connecting to Name) | `IP:127.0.0.1` | No | **Accepted** (HTTP 200) | **Fail** (Unexpected Success) |
| **CRL Distribution Points** | `wrong_cdp` | Invalid URI | Yes | **Rejected** (HTTP 525) | Pass (Rejected because unsupported when critical) |
| | `wrong_cdp_noncrit` | Invalid URI | No | **Accepted** (HTTP 200) | Pass (Ignored when non-critical) |
| **Authority Info Access** | `wrong_aia` | Invalid URI | Yes | **Rejected** (HTTP 525) | Pass (Rejected because unsupported when critical) |
| | `wrong_aia_noncrit` | Invalid URI | No | **Accepted** (HTTP 200) | Pass (Ignored when non-critical) |
| **Name Constraints** | `nc_server` | Violating CA's constraints | No | **Accepted** (HTTP 200) | **Fail** (Conscrypt failed to enforce) |
| | `nc_email_server` | Violating CA's email constraints | No | **Accepted** (HTTP 200) | **Fail** (Conscrypt failed to enforce) |
| **Policy Mapping** | N/A | - | - | Not Tested (Requires complex CA setup) | N/A |

## Detailed Findings

### 1. Rejection on Invalid Content (Expected Behavior)
Conscrypt correctly rejected certificates with invalid content in the following extensions:
*   **Basic Constraints**: Rejects if an end-entity certificate claims to be a CA.
*   **Extended Key Usage**: Rejects if the required purpose (`serverAuth`) is missing or incorrect.
*   **Subject Alternative Name**: Rejects if the hostname does not match the SAN.
*   **Certificate Policies**: Rejects if marked critical and containing unrecognized policies.

### 2. Behavior on Unsupported Extensions (CDP and AIA)
*   When marked **critical**, Conscrypt **rejected** the certificate because it does not support processing these extensions. This complies with RFC 5280 (rejecting unknown critical extensions).
*   When marked **non-critical**, Conscrypt **accepted** the certificate even though it contained invalid URIs, proving that it ignores them during path validation.

### 3. Failure to Enforce Name Constraints
Conscrypt **accepted** certificates that violated the Name Constraints specified by the issuing CA:
*   **DNS Constraints**: Accepted a certificate violating DNS name constraints (`nc_server`).
*   **Email Constraints**: Accepted a certificate violating email address constraints (`nc_email_server`).
This indicates a potential gap in Conscrypt's path validation regarding Name Constraints enforcement for multiple name types.

### 4. Policy Mapping
This extension was not tested due to the complexity of setting up a cross-certified CA infrastructure required to trigger policy mapping logic.

### 5. SAN Verification Quirks (Loopback Equivalence)
*   **DNS vs IP**: Conscrypt accepted a certificate with `DNS:localhost` when connecting to `127.0.0.1`.
*   **IP vs DNS**: Conscrypt accepted a certificate with `IP:127.0.0.1` when connecting to `localhost`.
This suggests that Conscrypt (or the underlying OkHttp client) treats `localhost` and `127.0.0.1` as equivalent for hostname verification purposes on the loopback interface. While practically safe for local testing, it may represent a deviation from strict RFC 6125 verification where type matching is expected.

## Conclusion
Conscrypt generally handles most extensions correctly according to standard expectations (rejecting bad values in critical extensions, enforcing EKU and SAN). However, it ignores non-critical CDP/AIA even if they contain garbage, and it failed to enforce Name Constraints in our test scenario. Additionally, it exhibits a loopback equivalence behavior for SAN verification.
