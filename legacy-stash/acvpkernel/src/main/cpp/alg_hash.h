#ifndef EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_HASH_H_
#define EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_HASH_H_

void af_alg_hash(message *msg, char *hash_name);
void af_alg_hash_mct(message *msg, char *hash_name);
void af_alg_hmac(message *msg, char *hash_name);
void af_alg_cmac(message *msg, char *hash_name, int verify);

#endif  // EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_HASH_H_