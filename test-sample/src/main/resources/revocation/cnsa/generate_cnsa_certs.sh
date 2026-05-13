#!/bin/bash
set -e

DIR="/Users/wkouki/AndroidStudioProjects/testbedui-plugins/test-sample/src/main/resources/revocation/cnsa"
cd $DIR

# Generate Root CA
openssl genrsa -out root-ca.key 3072
openssl req -new -x509 -days 3650 -key root-ca.key -out root-ca.crt -subj "/CN=Test CNSA Root CA" -sha384

# Generate Server Key and CSR
openssl genrsa -out server-valid.key 3072
openssl req -new -key server-valid.key -out server-valid.csr -subj "/C=JP/O=TestbedUI/CN=localhost"

# Server Extensions
echo "subjectAltName=DNS:localhost,IP:127.0.0.1" > server.ext
echo "authorityInfoAccess=OCSP;URI:http://localhost:8889" >> server.ext

# Sign Server Cert
openssl x509 -req -days 365 -in server-valid.csr -CA root-ca.crt -CAkey root-ca.key -CAcreateserial -out server-valid.crt -sha384 -extfile server.ext

# Generate Responder Key and CSR
openssl genrsa -out responder.key 3072
openssl req -new -key responder.key -out responder.csr -subj "/CN=Test CNSA OCSP Responder"

# Responder Extensions
echo "extendedKeyUsage=1.3.6.1.5.5.7.3.9" > responder.ext

# Sign Responder Cert
openssl x509 -req -days 365 -in responder.csr -CA root-ca.crt -CAkey root-ca.key -CAcreateserial -out responder.crt -sha384 -extfile responder.ext

# Clean up ext files
rm server.ext responder.ext

echo "Certs generated successfully!"
