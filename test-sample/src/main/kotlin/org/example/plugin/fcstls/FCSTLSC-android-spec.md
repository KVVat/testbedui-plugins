[TLS Package v2 SFRs](https://docs.google.com/document/d/1aWueSH5YYMHFra_ioK1Bgj_0XeMynGb_4XjIbdsiz14/edit?usp=sharing) (old reference with useful comments)

# TLS Client SFRs

## Cryptographic Support (FCS)

### FCS\_TLSC\_EXT.1 TLS Client Protocol

FCS\_TLSC\_EXT.1.1  
The TSF shall implement \[**selection**: *TLS 1.2 (RFC 5246), TLS 1.3 (RFC 8446\)*\] as a client that supports additional functionality for session renegotiation protection and \[**selection**:

* *mutual authentication*  
* *supplemental downgrade protection*  
* *session resumption*  
* *no optional functionality*

\] and shall abort attempts by a server to negotiate any TLS or SSL version prior to TLS 1.2 (RFC 5246).

FCS\_TLSC\_EXT.1.2  
The TSF shall be able to support the following \[**selection**:

* *TLS 1.2 ciphersuites: \[**selection**:*  
  * *CNSA 1.0 compliant \[**selection**:*  
    * *TLS\_ECDHE\_ECDSA\_WITH\_AES\_256\_GCM\_SHA384 as defined in RFC 5289 and RFC 8422*  
    * *TLS\_ECDHE\_RSA\_WITH\_AES\_256\_GCM\_SHA384 as defined in RFC 5289 and RFC 8422*  
    * *TLS\_DHE\_RSA\_WITH\_AES\_256\_GCM\_SHA384 as defined in RFC 5288*  
    * *TLS\_ECDHE\_ECDSA\_WITH\_AES\_256\_CBC\_SHA384 as defined in RFC 5289 and RFC 8422*  
    * *TLS\_ECDHE\_RSA\_WITH\_AES\_256\_CBC\_SHA384 as defined in RFC 5289 and RFC 8422*  
    * *ciphersuites using pre-shared secrets: \[**selection**:*  
      * *TLS\_ECDHE\_PSK\_WITH\_AES\_256\_GCM\_SHA384 as defined in RFC 8442*  
      * *TLS\_DHE\_PSK\_WITH\_AES\_256\_GCM\_SHA384 as defined in RFC 5487*  
      * *TLS\_RSA\_PSK\_WITH\_AES\_256\_GCM\_SHA384 as defined in RFC 5487*

      *\]*

    *\]*

  * *non-CNSA compliant \[**selection**:*  
    * *TLS\_RSA\_WITH\_AES\_256\_CBC\_SHA256 as defined in RFC 5246*  
    * *TLS\_RSA\_WITH\_AES\_256\_GCM\_SHA384 as defined in RFC 5288*  
    * *TLS\_DHE\_RSA\_WITH\_AES\_256\_CBC\_SHA256 as defined in RFC 5246*  
    * *TLS\_ECDHE\_ECDSA\_WITH\_AES\_128\_GCM\_SHA256 as defined in RFC 5289*  
    * *TLS\_ECDHE\_RSA\_WITH\_AES\_128\_GCM\_SHA256 as defined in RFC 5289*  
    * *TLS\_ECDHE\_ECDSA\_WITH\_AES\_128\_CBC\_SHA256 as defined in RFC 5289*  
    * *TLS\_ECDHE\_RSA\_WITH\_AES\_128\_CBC\_SHA256 as defined in RFC 5289*  
    * *TLS\_RSA\_WITH\_AES\_128\_CBC\_SHA256 as defined in RFC 5246*  
    * *TLS\_DHE\_RSA\_WITH\_AES\_128\_CBC\_SHA256 as defined in RFC 5246*  
    * *TLS\_RSA\_WITH\_AES\_128\_CBC\_SHA as defined in RFC 5246*  
    * *ciphersuites using pre-shared secrets: \[**selection**:*  
      * *TLS\_ECDHE\_PSK\_WITH\_AES\_128\_GCM\_SHA256 as defined in RFC 8442*  
      * *TLS\_DHE\_PSK\_WITH\_AES\_128\_GCM\_SHA256 as defined in RFC 5487*  
      * *TLS\_RSA\_PSK\_WITH\_AES\_128\_GCM\_SHA256 as defined in RFC 5487\]*

      *\]*

    *\]*

* *TLS 1.3 ciphersuites \[**selection**:*  
  * *CNSA 2.0 compliant TLS\_AES\_256\_GCM\_SHA384 as defined in RFC 8446*   
  * *non-CNSA compliant \[**selection**:*  
    * *TLS\_AES\_128\_GCM\_SHA256 as defined in RFC 8446*  
    * *\[**assignment**: other TLS 1.3 ciphersuites\]*

    *\]*

  *\]*

\] offering the supported ciphersuites in a ClientHello message in preference order: \[**assignment**: *list of supported ciphersuites*\].

FCS\_TLSC\_EXT.1.3  
The TSF shall not offer ClientHello messages indicating the following:

* null encryption  
* support for anonymous servers  
* use of cryptography that is deprecated, export-grade, or otherwise disallowed for encryption, including DES, 3DES, RC2, RC4, or IDEA  
* use of MD5 or SHA-1 for key derivation

and shall abort sessions where a server attempts to negotiate ciphersuites not enumerated in the ClientHello message.

FCS\_TLSC\_EXT.1.4  
The TSF shall be able to support the following TLS ClientHello message extensions:

* signature\_algorithms extension (RFC 8446\) indicating support for CNSA 1.0 compliant \[**selection**:  
  * *ecdsa\_secp384r1\_sha384 (RFC 8446\)*  
  * *rsa\_pkcs1\_sha384 (RFC 8446\)*

  \], and \[**selection**:

  * *CNSA 1.0 compliant \[**selection**:*  
    * *rsa\_pss\_pss\_sha384 (RFC 8446\)*  
    * *rsa\_pss\_rsae\_sha384 (RFC 8446\)*

    *\]*

  * *\[**assignment**: other non-deprecated, non-CNSA compliant signature algorithms\]*  
  * *no other signature algorithms*

  \], and

\[**selection**:

* *signature\_algorithms\_cert extension (RFC 8446\) indicating support for CNSA 1.0 compliant \[**selection**:*  
  * *ecdsa\_secp384r1\_sha384 (RFC 8446\)*  
  * *rsa\_pkcs1\_sha384 (RFC 8446\)*

  *\], and \[**selection**:*

  * *CNSA 1.0-compliant \[**selection**:*  
    * *rsa\_pss\_pss\_sha384 (RFC 8446\)*  
    * *rsa\_pss\_rsae\_sha384 (RFC 8446\)*

    *\]*

  * *non-CNSA compliant \[**selection**:*  
    * *rsa\_pkcs1\_sha256 (RFC 8446\)*  
    * *rsa\_pss\_rsae\_sha256 (RFC 8446\)*

    *\]*

  * *\[**assignment**: other non-deprecated, non-CNSA compliant signature algorithms\]*  
  * *no other signature algorithms*

  *\]*

* *supported\_versions extension (RFC 8446\) indicating support for TLS 1.3 and \[**selection**: TLS 1.2, no other versions\]*  
* *supported\_groups extension indicating support for \[**selection**:*  
  * *CNSA 1.0 compliant \[**selection**:*  
    * *secp384r1 (RFC 8446\)*  
    * *ffdhe3072 (RFC 7919\)*  
    * *ffdhe4096 (RFC 7919\)*

    *\]*

  * *non-CNSA compliant \[**selection**:*  
    * *secp256r1 (RFC 8446\)*  
    * *ffdhe2048 (RFC 7919\)*

    *\]*

  * *and \[**selection**:*  
    * *secp521r1 (RFC 8446\)*  
    * *ffdhe6144(RFC 7919\)*  
    * *ffdhe8192 (RFC 7919\)*  
    * *no other supported groups*

    *\]*

  *\]*

* *key\_share extension (RFC 8446\)*  
* *post\_handshake\_auth (RFC 8446), pre\_shared\_key (RFC 8446), tls\_cert\_with\_extern\_psk (RFC 8773), and psk\_key\_exchange\_modes (RFC 8446\) indicating psk\_dhe\_ke (DHE or ECDHE) mode*  
* *extended\_master\_secret extension (RFC 7627\) enforcing server support, and \[**selection**: allowing legacy servers, no other enforcement mode\]*  
* *no other extensions*

\] and shall not send the following extensions:

* early\_data  
* psk\_key\_exchange\_modes indicating PSK only mode.

FCS\_TLSC\_EXT.1.5  
The TSF shall be able to \[**selection**:

* *verify that a presented identifier of name type: \[**selection**:*  
  * *dNSName according to RFC 6125*  
  * *uniformResourceIdentifier according to RFC 6125*  
  * *SRVname according to RFC 6125*  
  * *Common Name conversion to dNSName according to RFC 5280 and RFC 6125*  
  * *directoryName according to RFC 5280*  
  * *IPAddress according to RFC 5280*  
  * *rfc822Name according to RFC 5280*  
  * *\[**assignment**: other name type\] according to \[**assignment**: RFC number\]*

  *\]*

* *interface with a supported function requesting the TLS channel to pass \[**selection**: the validated certification path, names of \[**assignment**: specified types\] extracted from the leaf certificate of a validated certification path, normalized representations of names of \[**assignment**: specified types\] extracted from the leaf certificate of a validated certification path\] for verification that a presented identifier*  
* *pass initial name constraints to the certification path processing function to verify, in accordance with FIA\_X509\_EXT.1, that the presented identifier*  
* *associate a PSK with a valid server with an identifier that*

\] matches a reference identifier for the requested TLS server and shall abort the session if no match is found.

FCS\_TLSC\_EXT.1.6  
The TSF shall not establish a trusted channel if \[**selection**:

* *the server certificate is invalid \[**selection**: with no TLS-specific exceptions, except when override is authorized in accordance with \[**assignment**: override rules\] in the case where valid revocation information is not available\]*  
* *a PSK associated with the server is invalid*

\].

### FCS\_TLSC\_EXT.2 TLS Client Support for Mutual Authentication

(based on choosing support for mutual authentication)

FCS\_TLSC\_EXT.2.1  
The TSF shall support mutual TLS authentication using X.509v3 certificates during the handshake and \[**selection**: *in support of post-handshake authentication requests, at no other time*\], in accordance with \[**selection**: *RFC 5246, Section 7.4.4, RFC 8446, Section 4.3.2*\].

### FCS\_TLSC\_EXT.3 TLS Client Downgrade Protection

(based on choosing support for downgrade protection)

FCS\_TLSC\_EXT.3.1  
The TSF shall not establish a TLS channel if the ServerHello message includes \[**selection**: *TLS 1.2 downgrade indicator, TLS 1.1 or below downgrade indicator*\] in the server random field.

### FCS\_TLSC\_EXT.4 TLS Client Support for Renegotiation

(based on choosing support for renegotiation)  
FCS\_TLSC\_EXT.4.1  
*The TSF shall support secure TLS renegotiation through use of \[**selection**:*

* *the “renegotiation\_info” TLS extension*  
* *the TLS\_EMPTY\_RENEGOTIATION\_INFO\_SCSV signaling ciphersuite signaling value in accordance with RFC 5746*  
* *rejection of all renegotiation attempts*

\] and shall terminate the session if an unexpected ServerHello is received or \[**selection**: *hello request message is received, in no other case*\].

### FCS\_TLSC\_EXT.5 TLS Client Support for Session Resumption

(based on choosing support for session resumption)

FCS\_TLSC\_EXT.5.1  
The TSF shall support session resumption as a TLS client via the use of \[**selection**: *session ID in accordance with RFC 5246, tickets in accordance with RFC 5077, PSK and tickets in accordance with RFC 8446*\].

### FCS\_TLSC\_EXT.6 TLS Client TLS 1.3 Resumption Refinements

(based on the selections about resumption)

FCS\_TLSC\_EXT.6.1  
The TSF shall send a psk\_key\_exchange\_modes extension with the value psk\_dhe\_ke when TLS 1.3 session resumption is offered.

FCS\_TLSC\_EXT.6.2  
The TSF shall not send early data in TLS 1.3 sessions.  
