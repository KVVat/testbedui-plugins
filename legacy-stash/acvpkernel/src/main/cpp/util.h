#ifndef EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_UTIL_H_
#define EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_UTIL_H_

#define CHECK_OOM(val)  if (!val) {fprintf(stderr, "Allocation failed"); exit(1);}

char **read_af_alg_config();

typedef enum {
    SHA1,
    SHA224,
    SHA256,
    SHA384,
    SHA512,
    HMAC_SHA1,
    HMAC_SHA224,
    HMAC_SHA256,
    HMAC_SHA384,
    HMAC_SHA512,
    ECB_AES,
    CTR_AES,
    CBC_AES,
    CTS_CBC_AES,
    CMAC_AES,
    XTS_AES,
    GCM_AES,
    DRBG_HMAC_SHA1,
    DRBG_HMAC_SHA224,
    DRBG_HMAC_SHA256,
    DRBG_HMAC_SHA384,
    DRBG_HMAC_SHA512,
    AF_ALG_ALGO_COUNT
} af_alg_algo;

#endif  // EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_UTIL_H_