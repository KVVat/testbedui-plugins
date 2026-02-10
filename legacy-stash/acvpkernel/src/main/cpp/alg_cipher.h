#ifndef EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_CIPHER_H_
#define EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_CIPHER_H_

void af_alg_aes_multi(message *msg, char *cipher_name, int encrypt);
void af_alg_aes_ecb(message *msg, char *cipher_name, int encrypt);
void af_alg_aes_single(message *msg, char *cipher_name, int encrypt);

#endif  // EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_CIPHER_H_