#!/bin/bash
set -e

DIR="/Users/wkouki/AndroidStudioProjects/testbedui-plugins/test-sample/src/main/resources/revocation/ecdsa"
cd $DIR

# Generate Root CA Key (EC P-384)
openssl ecparam -name secp384r1 -genkey -out root-ca.key
# Generate Root CA Cert
openssl req -new -x509 -days 3650 -key root-ca.key -out root-ca.crt -subj "/CN=Test ECDSA Root CA" -sha384

# Generate Server Key (EC P-384) and CSR
openssl ecparam -name secp384r1 -genkey -out server-valid.key
openssl req -new -key server-valid.key -out server-valid.csr -subj "/C=JP/O=TestbedUI/CN=localhost"

# Server Extensions
echo "subjectAltName=DNS:localhost,IP:127.0.0.1" > server.ext
echo "authorityInfoAccess=OCSP;URI:http://localhost:8890" >> server.ext

# Sign Server Cert
openssl x509 -req -days 365 -in server-valid.csr -CA root-ca.crt -CAkey root-ca.key -CAcreateserial -out server-valid.crt -sha384 -extfile server.ext

# Generate Responder Key (EC P-384) and CSR
openssl ecparam -name secp384r1 -genkey -out responder.key
openssl req -new -key responder.key -out responder.csr -subj "/CN=Test ECDSA OCSP Responder"

# Responder Extensions
echo "extendedKeyUsage=1.3.6.1.5.5.7.3.9" > responder.ext

# Sign Responder Cert
openssl x509 -req -days 365 -in responder.csr -CA root-ca.crt -CAkey root-ca.key -CAcreateserial -out responder.crt -sha384 -extfile responder.ext

# Clean up ext files
rm server.ext responder.ext

echo "Certs generated successfully!"
