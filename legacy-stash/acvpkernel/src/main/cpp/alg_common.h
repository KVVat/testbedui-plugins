#ifndef EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_COMMON_H_
#define EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_COMMON_H_

#ifndef ALG_SET_AEAD_ASSOCLEN
#define ALG_SET_AEAD_ASSOCLEN 4
#endif

#ifndef ALG_SET_AEAD_AUTHSIZE
#define ALG_SET_AEAD_AUTHSIZE 5
#endif

#ifndef ALG_SET_DRBG_ENTROPY
#define ALG_SET_DRBG_ENTROPY 6
#endif

#ifndef SOL_ALG
#define SOL_ALG 279
#endif

int create_algo_socket(char const *type, char *name);
void destroy_algo_socket(int algo);
int af_alg_cipher_parameters(int op, unsigned int enc, unsigned char *data,
                             int data_len, unsigned char *iv, int ivsize,
                             unsigned char *assoc, int assoc_len);

#endif  // EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_COMMON_H_