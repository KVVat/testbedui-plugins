#!/bin/bash

# Directory to store certs
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"

echo "Generating Real Root CA..."
openssl req -new -x509 -days 3650 -nodes -newkey rsa:2048 -keyout root-ca.key -out root-ca.crt -subj "/C=JP/O=TestbedUI/CN=Real Root CA" -sha384

echo "Generating Fake Root CA..."
openssl req -new -x509 -days 3650 -nodes -newkey rsa:2048 -keyout fake-ca.key -out fake-ca.crt -subj "/C=JP/O=TestbedUI/CN=Fake Root CA" -sha384

echo "Generating Name Constraints CA..."
openssl genrsa -out nc-ca.key 2048
openssl req -new -key nc-ca.key -out nc-ca.csr -subj "/C=JP/O=TestbedUI/CN=NC Root CA"
cat <<EOF > nc_ca.ext
basicConstraints=critical,CA:true
nameConstraints=critical,permitted;DNS:example.com
subjectKeyIdentifier=hash
keyUsage = critical, keyCertSign, cRLSign
EOF
openssl x509 -req -days 3650 -in nc-ca.csr -signkey nc-ca.key -out nc-ca.crt -extfile nc_ca.ext -sha384
rm nc-ca.csr nc_ca.ext

# Helper to generate CSR
gen_csr() {
    name=$1
    subj=$2
    openssl req -new -nodes -newkey rsa:2048 -keyout ${name}.key -out ${name}.csr -subj "$subj"
}

# Helper to sign cert with extensions
sign_cert() {
    name=$1
    extfile=$2
    ca=$3
    cakey=$4
    openssl x509 -req -days 365 -in ${name}.csr -CA $ca -CAkey $cakey -CAcreateserial -out ${name}.crt -extfile "$extfile" -sha384
}

echo "Generating CSRs..."
gen_csr "ok_all" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "no_akid" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "no_skid" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "no_keyusage" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "no_san" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_keyusage" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_eku" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_akid" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_basic_constraints" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_san" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_skid" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_policies" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "nc_server" "/C=JP/O=TestbedUI/CN=localhost"

gen_csr "wrong_cdp" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_cdp_noncrit" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "wrong_aia" "/C=JP/O=TestbedUI/CN=localhost"

gen_csr "wrong_aia_noncrit" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "all_wrong" "/C=JP/O=TestbedUI/CN=wronghost"
gen_csr "san_dns_only" "/C=JP/O=TestbedUI/CN=localhost"
gen_csr "san_ip_only" "/C=JP/O=TestbedUI/CN=localhost"


echo "Creating extension files..."

# OK All
cat <<EOF > ok_all.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# No AKID
cat <<EOF > no_akid.ext
subjectKeyIdentifier=hash
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# No SKID
cat <<EOF > no_skid.ext
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# No KeyUsage
cat <<EOF > no_keyusage.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# No SAN
cat <<EOF > no_san.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
EOF

# Wrong KeyUsage
cat <<EOF > wrong_keyusage.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, cRLSign
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# Wrong EKU
cat <<EOF > wrong_eku.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = clientAuth
EOF

# Wrong AKID (will be signed by fake CA)
cat <<EOF > wrong_akid.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# Wrong Basic Constraints
cat <<EOF > wrong_basic_constraints.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:true
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# Wrong SAN
cat <<EOF > wrong_san.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:example.com
extendedKeyUsage = serverAuth
EOF

# Wrong SKID
cat <<EOF > wrong_skid.ext
subjectKeyIdentifier=11:22:33:44:55:66:77:88:99:00:11:22:33:44:55:66:77:88:99:00
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# Wrong Policies
cat <<EOF > wrong_policies.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
certificatePolicies = critical, 1.2.3.4.5
EOF

# Wrong CDP
cat <<EOF > wrong_cdp.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
crlDistributionPoints = critical, URI:http://nonexistent.example.com/crl.crl
EOF

# Wrong CDP Non-Critical
cat <<EOF > wrong_cdp_noncrit.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
crlDistributionPoints = URI:http://nonexistent.example.com/crl.crl
EOF

# Wrong AIA
cat <<EOF > wrong_aia.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
authorityInfoAccess = critical, OCSP;URI:http://nonexistent.example.com
EOF

# Wrong AIA Non-Critical
cat <<EOF > wrong_aia_noncrit.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost,IP:127.0.0.1
extendedKeyUsage = serverAuth
authorityInfoAccess = OCSP;URI:http://nonexistent.example.com
EOF

# All Wrong
cat <<EOF > all_wrong.ext
subjectKeyIdentifier=hash
basicConstraints = critical,CA:false
keyUsage = critical, cRLSign
extendedKeyUsage = clientAuth
EOF

# SAN DNS Only
cat <<EOF > san_dns_only.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost
extendedKeyUsage = serverAuth
EOF

# SAN IP Only
cat <<EOF > san_ip_only.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# SAN Email Violating Constraints
cat <<EOF > nc_email_server.ext
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical,CA:false
keyUsage = critical, digitalSignature, keyEncipherment
subjectAltName = DNS:localhost, email:user@test.com
extendedKeyUsage = serverAuth
EOF

echo "Generating Email Name Constraints CA..."
openssl genrsa -out nc-email-ca.key 2048
openssl req -new -key nc-email-ca.key -out nc-email-ca.csr -subj "/C=JP/O=TestbedUI/CN=NC Email Root CA"
cat <<EOF > nc_email_ca.ext
basicConstraints=critical,CA:true
nameConstraints=critical,permitted;email:.example.com
subjectKeyIdentifier=hash
keyUsage = critical, keyCertSign, cRLSign
EOF
openssl x509 -req -days 3650 -in nc-email-ca.csr -signkey nc-email-ca.key -out nc-email-ca.crt -extfile nc_email_ca.ext -sha384
rm nc-email-ca.csr nc_email_ca.ext

echo "Signing certificates..."
sign_cert "ok_all" "ok_all.ext" "root-ca.crt" "root-ca.key"
sign_cert "no_akid" "no_akid.ext" "root-ca.crt" "root-ca.key"
sign_cert "no_skid" "no_skid.ext" "root-ca.crt" "root-ca.key"
sign_cert "no_keyusage" "no_keyusage.ext" "root-ca.crt" "root-ca.key"
sign_cert "no_san" "no_san.ext" "root-ca.crt" "root-ca.key"
sign_cert "wrong_keyusage" "wrong_keyusage.ext" "root-ca.crt" "root-ca.key"
sign_cert "wrong_eku" "wrong_eku.ext" "root-ca.crt" "root-ca.key"
sign_cert "wrong_akid" "wrong_akid.ext" "fake-ca.crt" "fake-ca.key"
sign_cert "wrong_basic_constraints" "wrong_basic_constraints.ext" "root-ca.crt" "root-ca.key"
sign_cert "wrong_san" "wrong_san.ext" "root-ca.crt" "root-ca.key"
sign_cert "wrong_skid" "wrong_skid.ext" "root-ca.crt" "root-ca.key"
sign_cert "wrong_policies" "wrong_policies.ext" "root-ca.crt" "root-ca.key"
sign_cert "nc_server" "ok_all.ext" "nc-ca.crt" "nc-ca.key"
sign_cert "wrong_cdp_noncrit" "wrong_cdp_noncrit.ext" "root-ca.crt" "root-ca.key"
sign_cert "wrong_aia" "wrong_aia.ext" "root-ca.crt" "root-ca.key"
sign_cert "wrong_aia_noncrit" "wrong_aia_noncrit.ext" "root-ca.crt" "root-ca.key"

sign_cert "all_wrong" "all_wrong.ext" "root-ca.crt" "root-ca.key"
sign_cert "san_dns_only" "san_dns_only.ext" "root-ca.crt" "root-ca.key"
sign_cert "san_ip_only" "san_ip_only.ext" "root-ca.crt" "root-ca.key"
gen_csr "nc_email_server" "/C=JP/O=TestbedUI/CN=localhost"
sign_cert "nc_email_server" "nc_email_server.ext" "nc-email-ca.crt" "nc-email-ca.key"


echo "Cleaning up CSRs and ext files..."
rm *.csr *.ext

echo "Done!"
