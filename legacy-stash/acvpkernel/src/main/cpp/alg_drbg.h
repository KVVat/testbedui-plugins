#ifndef EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_DRBG_H_
#define EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_DRBG_H_

void af_alg_ctrdrbg(message *msg);
void af_alg_hmacdrbg(message *msg, char *mode);

#endif  // EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_ALG_DRBG_H_