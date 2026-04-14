# TLS/Network Test Redesign Memo (FCS_TLS_EXT.1)

## Objective
Redesign the test for `FCS_TLS_EXT.1` (formerly related to FTP_ITC) to remove heavy external dependencies (PCAPdroid, Wireshark/tshark) and use lightweight or built-in alternatives for packet capture and analysis.

## Current Implementation Analysis
- **Capture**: Uses `PCAPdroid` (an external Android app) launched via Intent. It requires UI Automator to click through permission and start dialogs, which is brittle and slow.
- **Analysis**: Pulls the PCAP file to the host and uses `tshark` (part of Wireshark) to convert it to PDML (XML format). Then it parses the XML in Kotlin using `SAXReader`.

## Proposed Redesign Options

### 1. Packet Capture on Android
The goal is to avoid external apps like PCAPdroid.

- **Option A: Android Built-in `tcpdump`**
  - If the test device is rooted or has an engineering build, `tcpdump` might be available natively.
  - We can execute `tcpdump` via ADB shell directly from the test code.
  - *Pros*: No external app needed, very reliable.
  - *Cons*: Requires root or specific device permissions.

- **Option B: Custom Minimal VPN Service**
  - Implement a minimal `VpnService` in our own test helper app (or integrate into the existing test app).
  - This service would intercept packets and write them to a PCAP file.
  - *Pros*: Works on non-rooted devices, full control over the capture process.
  - *Cons*: Requires implementing a basic IP packet capture loop.

- **User Note Clarification**: The user mentioned "pcapはandroid組み込みのモジュールで可能" (PCAP is possible with Android's built-in module). We need to clarify if this refers to `tcpdump` or a specific Android API they have in mind.

### 2. Packet Analysis (Wireshark Alternative)
The goal is to avoid calling `tshark` on the host and parsing large XML files.

- **Option A: Lightweight Java/Kotlin PCAP Parser Library**
  - Use a small, pure Java/Kotlin library to read PCAP files (e.g., `Pcap4J` in pure Java mode, or a minimal PCAP reader).
  - *Pros*: Easy to integrate, avoids external process calls.
  - *Cons*: Adds a dependency (unless we find a very small one or vendor it).

- **Option B: Custom Minimal Parser for TLS Client Hello**
  - Since we only need to verify:
    - TLS Version (1.2, 1.3)
    - Supported Cipher Suites
    - Certificate validity (optional, if we can check Alert packets)
  - We can implement a simple binary parser for PCAP files and extract the TLS handshake data (specifically the Client Hello).
  - *Pros*: Zero external dependencies, highly focused.
  - *Cons*: Requires understanding and implementing the binary layout of PCAP and TLS Handshake.

## Next Steps
1. **Clarify**: Ask the user what "android組み込みのモジュール" refers to.
2. **Select**: Choose the capture and analysis methods based on the clarification and constraints.
3. **Prototype**: Implement a proof-of-concept for the selected approach.
