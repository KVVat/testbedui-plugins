#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
OUT_DIR="${REPO_ROOT}/test-sample/src/main/resources/revocation"
mkdir -p "${OUT_DIR}"

echo "[gen-ocsp-certs] Generating CA..."
openssl genrsa -out "${OUT_DIR}/root-ca.key" 2048
openssl req -x509 -new -nodes -key "${OUT_DIR}/root-ca.key" -sha256 -days 3650 -subj "/C=JP/O=TestbedUI/CN=Test OCSP Root CA" -out "${OUT_DIR}/root-ca.crt" -config <(cat <<EOF
[req]
distinguished_name = req_dn
x509_extensions = v3_ca
prompt = no
[req_dn]
CN = Test OCSP Root CA
[v3_ca]
basicConstraints = critical, CA:TRUE
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
EOF
)

echo "[gen-ocsp-certs] Generating Responder Cert..."
openssl genrsa -out "${OUT_DIR}/responder.key" 2048
openssl req -new -key "${OUT_DIR}/responder.key" -subj "/C=JP/O=TestbedUI/CN=OCSP Responder" -out "${OUT_DIR}/responder.csr"
openssl x509 -req -in "${OUT_DIR}/responder.csr" -CA "${OUT_DIR}/root-ca.crt" -CAkey "${OUT_DIR}/root-ca.key" -CAcreateserial -days 365 -sha256 -out "${OUT_DIR}/responder.crt" -extfile <(cat <<EOF
extendedKeyUsage = critical, OCSPSigning
EOF
)

echo "[gen-ocsp-certs] Generating Valid Server Cert..."
openssl genrsa -out "${OUT_DIR}/server-valid.key" 2048
openssl req -new -key "${OUT_DIR}/server-valid.key" -subj "/C=JP/O=TestbedUI/CN=localhost" -out "${OUT_DIR}/server-valid.csr"
openssl x509 -req -in "${OUT_DIR}/server-valid.csr" -CA "${OUT_DIR}/root-ca.crt" -CAkey "${OUT_DIR}/root-ca.key" -CAcreateserial -days 365 -sha256 -out "${OUT_DIR}/server-valid.crt" -extfile <(cat <<EOF
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = DNS:localhost, IP:127.0.0.1, IP:10.0.2.2
authorityInfoAccess = OCSP;URI:http://localhost:8888
EOF
)

echo "[gen-ocsp-certs] Generating Revoked Server Cert..."
openssl genrsa -out "${OUT_DIR}/server-revoked.key" 2048
openssl req -new -key "${OUT_DIR}/server-revoked.key" -subj "/C=JP/O=TestbedUI/CN=revoked.localhost" -out "${OUT_DIR}/server-revoked.csr"
openssl x509 -req -in "${OUT_DIR}/server-revoked.csr" -CA "${OUT_DIR}/root-ca.crt" -CAkey "${OUT_DIR}/root-ca.key" -CAcreateserial -days 365 -sha256 -out "${OUT_DIR}/server-revoked.crt" -extfile <(cat <<EOF
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = DNS:revoked.localhost, IP:10.0.2.2
authorityInfoAccess = OCSP;URI:http://localhost:8888
EOF
)

echo "[gen-ocsp-certs] Generating SHA384 Server Cert..."
openssl req -new -key "${OUT_DIR}/server-valid.key" -subj "/C=JP/O=TestbedUI/CN=localhost" -out "${OUT_DIR}/server-sha384.csr"
openssl x509 -req -in "${OUT_DIR}/server-sha384.csr" -CA "${OUT_DIR}/root-ca.crt" -CAkey "${OUT_DIR}/root-ca.key" -CAcreateserial -days 365 -sha384 -out "${OUT_DIR}/server-sha384.crt" -extfile <(cat <<EOF
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = DNS:localhost, IP:127.0.0.1, IP:10.0.2.2
authorityInfoAccess = OCSP;URI:http://localhost:8888
EOF
)

# Create index.txt for OCSP responder
valid_serial=$(openssl x509 -in "${OUT_DIR}/server-valid.crt" -noout -serial | cut -d= -f2)
sha384_serial=$(openssl x509 -in "${OUT_DIR}/server-sha384.crt" -noout -serial | cut -d= -f2)
revoked_serial=$(openssl x509 -in "${OUT_DIR}/server-revoked.crt" -noout -serial | cut -d= -f2)

echo -e "V\t301231235959Z\t\t${valid_serial}\tunknown\t/C=JP/O=TestbedUI/CN=localhost" > "${OUT_DIR}/index.txt"
echo -e "V\t301231235959Z\t\t${sha384_serial}\tunknown\t/C=JP/O=TestbedUI/CN=localhost" >> "${OUT_DIR}/index.txt"
echo -e "R\t301231235959Z\t260512000000Z\t${revoked_serial}\tunknown\t/C=JP/O=TestbedUI/CN=revoked.localhost" >> "${OUT_DIR}/index.txt"

# Disable unique subject check in index database
echo "unique_subject = no" > "${OUT_DIR}/index.txt.attr"

echo "[gen-ocsp-certs] Done generating certs in ${OUT_DIR}"
