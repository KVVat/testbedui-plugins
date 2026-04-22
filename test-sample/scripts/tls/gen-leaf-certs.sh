#!/usr/bin/env bash
#
# Generates leaf certificates signed by the Custom Test Root CA.
# Requires gen-test-ca.sh to have been run first.
#
# Leaf set (fixed for FCS_TLSC_EXT 4.2 / 4.3 work plan):
#   - leaf-localhost-dns   : SAN DNS:localhost
#   - leaf-ip-127.0.0.1    : SAN IP:127.0.0.1
#   - leaf-ip-wrong        : SAN IP:10.0.0.99
#   - leaf-uri             : SAN URI:urn:example:tls-test
#
# Each leaf produces: <name>.key, <name>.csr, <name>.crt, <name>.p12
# P12 bundles use the password 'changeit' (override via P12_PASSWORD).
#
# Idempotent: existing <name>.crt is skipped unless FORCE=1 is set.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
OUT_DIR="${OUT_DIR:-${REPO_ROOT}/test-sample/src/main/resources/tls}"

CA_KEY="${OUT_DIR}/test-root-ca.key"
CA_CRT="${OUT_DIR}/test-root-ca.crt"
CA_SRL="${OUT_DIR}/test-root-ca.srl"

LEAF_DAYS="${LEAF_DAYS:-825}"
LEAF_KEY_BITS="${LEAF_KEY_BITS:-2048}"
P12_PASSWORD="${P12_PASSWORD:-changeit}"

if [[ ! -f "${CA_KEY}" || ! -f "${CA_CRT}" ]]; then
  echo "[gen-leaf-certs] Root CA not found. Run gen-test-ca.sh first." >&2
  exit 1
fi

mkdir -p "${OUT_DIR}"

# --- helpers ---------------------------------------------------------------

# $1 = leaf name (basename), $2 = CN, $3 = subjectAltName value
gen_leaf() {
  local name="$1"
  local cn="$2"
  local san="$3"

  local key="${OUT_DIR}/${name}.key"
  local csr="${OUT_DIR}/${name}.csr"
  local crt="${OUT_DIR}/${name}.crt"
  local p12="${OUT_DIR}/${name}.p12"

  if [[ -f "${crt}" && "${FORCE:-0}" != "1" ]]; then
    echo "[gen-leaf-certs] ${name}: already present; skipping (FORCE=1 to regenerate)"
    return 0
  fi

  echo "[gen-leaf-certs] ${name}: CN='${cn}' SAN='${san}'"

  openssl genrsa -out "${key}" "${LEAF_KEY_BITS}"
  chmod 600 "${key}"

  local cfg
  cfg="$(mktemp)"
  cat > "${cfg}" <<EOF
[req]
distinguished_name = req_dn
req_extensions     = v3_req
prompt             = no
[req_dn]
C  = JP
O  = TestbedUI
OU = FCS_TLSC_EXT Test Leaf
CN = ${cn}
[v3_req]
basicConstraints     = CA:FALSE
keyUsage             = critical, digitalSignature, keyEncipherment
extendedKeyUsage     = serverAuth
subjectAltName       = ${san}
subjectKeyIdentifier = hash
EOF

  openssl req -new -key "${key}" -out "${csr}" -config "${cfg}"

  openssl x509 -req \
    -in "${csr}" \
    -CA "${CA_CRT}" \
    -CAkey "${CA_KEY}" \
    -CAserial "${CA_SRL}" \
    -days "${LEAF_DAYS}" \
    -sha256 \
    -extfile "${cfg}" \
    -extensions v3_req \
    -out "${crt}"

  rm -f "${cfg}" "${csr}"

  openssl pkcs12 -export \
    -inkey "${key}" \
    -in "${crt}" \
    -certfile "${CA_CRT}" \
    -name "${name}" \
    -passout "pass:${P12_PASSWORD}" \
    -out "${p12}"

  echo "[gen-leaf-certs] ${name}: wrote ${crt} and ${p12}"
}

# --- leaf definitions ------------------------------------------------------

gen_leaf "leaf-localhost-dns" "localhost"          "DNS:localhost"
gen_leaf "leaf-ip-127.0.0.1"  "127.0.0.1"          "IP:127.0.0.1"
gen_leaf "leaf-ip-wrong"      "10.0.0.99"          "IP:10.0.0.99"
gen_leaf "leaf-uri"           "urn:example:tls-test" "URI:urn:example:tls-test"

echo "[gen-leaf-certs] Done. P12 password: ${P12_PASSWORD}"
