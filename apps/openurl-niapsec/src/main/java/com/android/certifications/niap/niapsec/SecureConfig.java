package com.android.certifications.niap.niapsec;

import com.android.certifications.niap.niapsec.config.TrustAnchorOptions;

public class SecureConfig {
    public static final String PACKAGE_NAME = "com.android.certifications.niap.niapsec";
    public static final String ANDROID_CA_STORE = "AndroidCAStore";
    public static final String SSL_TLS = "TLS";

    public static SecureConfig getStrongConfig() {
        return new SecureConfig();
    }

    public boolean isUseStrongSSLCiphersEnabled() {
        return true;
    }

    public String getCertPath() {
        return "X.509";
    }

    public String getAndroidCAStore() {
        return ANDROID_CA_STORE;
    }

    public String getCertPathValidator() {
        return "PKIX";
    }

    public String getKeystoreType() {
        return "PKCS12";
    }

    public TrustAnchorOptions getTrustAnchorOptions() {
        return TrustAnchorOptions.USER_SYSTEM;
    }

    public String[] getStrongSSLCiphers() {
        return new String[]{
                "TLS_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_RSA_WITH_AES_256_CBC_SHA256",
                "TLS_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
                "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384"
        };
    }
}
