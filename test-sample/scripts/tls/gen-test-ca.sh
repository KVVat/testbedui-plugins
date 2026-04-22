#!/usr/bin/env bash
#
# Generates a Custom Test Root CA for FCS_TLSC_EXT local testing.
#
# Output: <OUT_DIR>/test-root-ca.{key,crt,srl}
# Default OUT_DIR: test-sample/src/main/resources/tls (relative to repo root)
#
# Idempotent: if test-root-ca.key already exists, the script exits without
# overwriting. Pass FORCE=1 to regenerate (this invalidates any previously
# signed leaf certs and any CA installation on the TOE).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
OUT_DIR="${OUT_DIR:-${REPO_ROOT}/test-sample/src/main/resources/tls}"

CA_KEY="${OUT_DIR}/test-root-ca.key"
CA_CRT="${OUT_DIR}/test-root-ca.crt"
CA_SRL="${OUT_DIR}/test-root-ca.srl"

CA_SUBJECT="${CA_SUBJECT:-/C=JP/O=TestbedUI/OU=FCS_TLSC_EXT Test/CN=Testbed Custom Test Root CA}"
CA_DAYS="${CA_DAYS:-3650}"
CA_KEY_BITS="${CA_KEY_BITS:-4096}"

mkdir -p "${OUT_DIR}"

if [[ -f "${CA_KEY}" && "${FORCE:-0}" != "1" ]]; then
  echo "[gen-test-ca] ${CA_KEY} already exists; skipping (set FORCE=1 to regenerate)."
  exit 0
fi

echo "[gen-test-ca] Output dir : ${OUT_DIR}"
echo "[gen-test-ca] Subject    : ${CA_SUBJECT}"
echo "[gen-test-ca] Validity   : ${CA_DAYS} days"

openssl genrsa -out "${CA_KEY}" "${CA_KEY_BITS}"
chmod 600 "${CA_KEY}"

openssl req -x509 -new -nodes \
  -key "${CA_KEY}" \
  -sha256 \
  -days "${CA_DAYS}" \
  -subj "${CA_SUBJECT}" \
  -extensions v3_ca \
  -config <(cat <<'EOF'
[req]
distinguished_name = req_dn
x509_extensions    = v3_ca
prompt             = no
[req_dn]
CN = placeholder
[v3_ca]
basicConstraints       = critical, CA:TRUE
keyUsage               = critical, keyCertSign, cRLSign
subjectKeyIdentifier   = hash
authorityKeyIdentifier = keyid:always
EOF
) \
  -out "${CA_CRT}"

echo "01" > "${CA_SRL}"

echo "[gen-test-ca] Wrote:"
echo "  - ${CA_KEY}"
echo "  - ${CA_CRT}"
echo "  - ${CA_SRL}"
echo "[gen-test-ca] Done."
