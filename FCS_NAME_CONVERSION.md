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
Treat "EXT" as a regular word. Capitalize only the first letter.

* `..._EXT...` -> `...Ext...`

### 2.4 Version Numbers
Connect numbers directly unless ambiguity arises.

* `.1` -> `1`
* `.1.2` -> `12`

## 3. Conversion Examples

| Original FSR ID | Legacy Naming (Snake_Case) | **Modern Naming (UpperCamelCase)** | Notes |
| :--- | :--- | :--- | :--- |
| `FDP_ACC.1` | `FDP_ACC1` | **`FdpAcc1Test`** | Standard |
| `FDP_ACF_EXT.1` | `FDP_ACF_EXT` | **`FdpAcfExt1Test`** | "Ext" is capitalized |
| `FPR_PSE.1` | `FPR_PSE1` | **`FprPse1Test`** | |
| `FCS_CKH_EXT.1` | `FCS_CKH_EXT1` | **`FcsCkhExt1Test`** | |
| `FCS_CKH.1/Low` | `FCS_CKH_1_LOW` | **`FcsCkh1LowTest`** | Slash handled as separator |
| `FCS_CKH.1/High` | `FCS_CKH_EXT1_HIGH` | **`FcsCkh1HighTest`** | |
| `FTP_ITC_EXT.1/TLS` | `FTP_ITC_EXT1` | **`FtpItcExt1TlsTest`** | Complex ID with slash |

## 4. Package Structure

We recommend grouping these tests into packages based on the family (first 3-6 letters) to keep the root directory clean.

**Example Structure:**

```text
src/main/kotlin/org/example/plugin/
  ├── fdpacc1/
  │    └── FdpAcc1Test.kt       (FDP_ACC.1)
  ├── fcsckh/
  │    ├── FcsCkh1LowTest.kt    (FCS_CKH.1/Low)
  │    └── FcsCkh1HighTest.kt   (FCS_CKH.1/High)
  └── ftpitc/
       └── FtpItcExt1TlsTest.kt (FTP_ITC_EXT.1/TLS)