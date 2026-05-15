# SDP File Write/Read Library Design note

Agent : Kouki Watanabe

**1\. Introduction**

* **1.1 Goal and Scope:** The primary goal of this project is to simplify and modernize the handling of sensitive data protection (SDP) file I/O.  
* **1.2 Audience:** Internal and the collaborative vendors.  
* **1.3 Definitions and Acronyms:**   
  1. SDP : Sensitive Data Protection

**2\. Background and Motivation**

* **2.1 Problem Statement:** The existing solution for handling sensitive data protection files I/O is based on a raw level and legacy implementation. This architecture has resulted in:  
  * Code fragmentation and complexity  
  * Lack of Standardization  
  * Platform Integration issues.  
* **2.2 Justification:**   
  * Provide a standardized way to read/write SDP files on the android os.  
    * It can support the NIAP standard in high level and secure.  
    * We will base on the Google Tink Library, and leverage the android keystore (Hardware Backed Keystore).  
      * [https://github.com/tink-crypto/tink](https://github.com/tink-crypto/tink)  
      * ’s not the fastest way but difficult to crack.  
      * Tink supports streaming files and use cases, so   
* **2.3 Requirements:**   
  * OS : android os sdk level 32  
  * Must handle files \< 2GB  
  * Supports encryption on the locked devices.  
* **2.3 Schedule:**  
  * Prototyping : 2 weeks (+1week).  
  * Documentation : 1 week. 

**3\. High-Level Design and Architecture**

* **3.1 System Context:**   
  * The library can be included from gradle settings and initially placed on the github packages.  
* **3.2 Architectural Overview:**   
  * Provide Custom KeyManger for Tink  
    * Use AES256\_GCM for Encryption and Decryption.  
    * The encryption file header style will follow RFC9580(Open PGP).  
      * Currently the NISAPSEC library doesn’t follow any standard and it does not support file moves.  
    * Use Envelope Encryption technique  
      * We shouldn’t generate a key for each file, if we consider we should maintain thousands of the keys.  
    * To support the file move and name change, the encrypted file should hold a uuid in Notation Data section in the packet.

| \[Signature Packet (Tag 2)\]  \<-- Contains Metadata of File \[Packet Header\]   \-\> Version: 2   \-\> Symmetric-key Algorithm ID: (e.g., AES-256 is 9\)   \-\> AEAD Algorithm ID: (e.g., GCM is 1\)   \-\> Chunk Size: (Size parameter for incremental decryption) \[IV / Nonce\]   \-\> (e.g., 12 bytes for AES-GCM) \[Encrypted Data\]   \-\> (The ciphertext broken into chunks) \[Authentication Tag\]   \-\> (The MAC tag to verify data integrity) |
| :---- |

* **(Envelope Encryption)**   
  * Encryption:  
    * Generate a random DEK.  
    * Encrypt the file content using the DEK.  
    * Encrypt (wrap) the DEK itself using the Master Key from the KeyStore.  
  * Storage:  
    * The Encrypted DEK (Wrapped DEK) is embedded directly into the file's metadata (e.g., in a PKESK/SKESK packet under RFC 9580).  
  * Decryption:  
    * Extract the Wrapped DEK from the file.  
    * Use the Master Key in the KeyStore to decrypt the Wrapped DEK and retrieve the original DEK.  
    * Decrypt the file content using the DEK.  
* **3.3 Key Design Decisions:**   
  * Language : Java , android sdk  
  * Encryption method   
    * AES256GCM by default  
    * All keys are stored in AndroidKeyStore (Hardware Key Storage)