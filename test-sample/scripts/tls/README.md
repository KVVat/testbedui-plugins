# TLS Test Certificate Generation

Scripts for generating the Custom Test Root CA and leaf certificates used by
the `FCS_TLSC_EXT` 4.2 / 4.3 work plan (see
[../../src/main/kotlin/org/example/plugin/fcstls/FCSTLSC-plan-4.2-4.3.md](../../src/main/kotlin/org/example/plugin/fcstls/FCSTLSC-plan-4.2-4.3.md)).

## Usage

```bash
# 1. Generate the Root CA (once)
./gen-test-ca.sh

# 2. Generate the leaf certificates (after CA exists)
./gen-leaf-certs.sh

# Regenerate everything from scratch:
FORCE=1 ./gen-test-ca.sh && FORCE=1 ./gen-leaf-certs.sh
```

## Output

All files land in `test-sample/src/main/resources/tls/` by default (override
with `OUT_DIR=/some/path`).

| File | Purpose |
|---|---|
| `test-root-ca.{crt,key,srl}` | Root CA — install `test-root-ca.crt` into the TOE user trust store |
| `leaf-localhost-dns.{crt,key,p12}` | SAN `DNS:localhost` (4.2 renegotiation test + 4.3 dNSName regression) |
| `leaf-ip-127.0.0.1.{crt,key,p12}` | SAN `IP:127.0.0.1` (4.3 IPAddress positive) |
| `leaf-ip-wrong.{crt,key,p12}` | SAN `IP:10.0.0.99` (4.3 IPAddress negative) |
| `leaf-uri.{crt,key,p12}` | SAN `URI:urn:example:tls-test` (4.3 URI — see plan §2.2 scope caveat) |

P12 password: `changeit` (override via `P12_PASSWORD=...`).

## Environment overrides

| Variable | Default | Notes |
|---|---|---|
| `OUT_DIR` | `test-sample/src/main/resources/tls` | Where to write artifacts |
| `FORCE` | `0` | Set to `1` to overwrite existing files |
| `CA_DAYS` | `3650` | Root CA validity |
| `CA_KEY_BITS` | `4096` | Root CA RSA size |
| `LEAF_DAYS` | `825` | Leaf validity (≤825 keeps within modern browser limits) |
| `LEAF_KEY_BITS` | `2048` | Leaf RSA size |
| `P12_PASSWORD` | `changeit` | P12 bundle password |

## Verify output

```bash
cd test-sample/src/main/resources/tls
openssl x509 -in test-root-ca.crt -noout -subject -issuer -dates
openssl verify -CAfile test-root-ca.crt leaf-localhost-dns.crt
openssl x509 -in leaf-ip-127.0.0.1.crt -noout -ext subjectAltName
```
