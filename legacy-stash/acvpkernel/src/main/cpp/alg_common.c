#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <linux/if_alg.h>
#include <sys/socket.h>

#include "alg_common.h"
#include "util.h"

int create_algo_socket(char const *type, char *name) {
    struct sockaddr_alg sa;
    memset(&sa, 0, sizeof(sa));
    sa.salg_family = AF_ALG;
    snprintf((char *) sa.salg_type, sizeof(sa.salg_type), "%s", type);
    snprintf((char *) sa.salg_name, sizeof(sa.salg_name), "%s", name);

    int algo = socket(AF_ALG, SOCK_SEQPACKET, 0);

    if (algo == -1) {
        fputs("Unable to create socket", stderr);
        return -1;
    }
    if (bind(algo, (struct sockaddr *) &sa, sizeof(sa)) == -1) {
        fputs("Unable to bind socket", stderr);
        return -1;
    }

    return algo;
}

void destroy_algo_socket(int algo) {
    close(algo);
}

int af_alg_cipher_parameters(int op, unsigned int enc, unsigned char *data,
                             int data_len, unsigned char *iv, int ivsize,
                             unsigned char *assoc, int assoc_len) {
    struct cmsghdr *header = NULL;
    uint32_t *type = NULL;
    struct msghdr msg;

    struct af_alg_iv *alg_iv = NULL;
    size_t iv_msg_size = ivsize ? CMSG_SPACE(sizeof(*alg_iv) + ivsize) : 0;

    uint32_t *assoclen = NULL;
    size_t assoc_msg_size = assoc_len ? CMSG_SPACE(sizeof(*assoclen)) : 0;

    /*
    Buffer structure is:
        int type - encryption / decryption
        optional iv[16]
        optional int aead_assoc_size;
    */

    size_t bufferlen = CMSG_SPACE(sizeof(*type)) + iv_msg_size + assoc_msg_size;

    memset(&msg, 0, sizeof(msg));

    char *buffer_p = (char *) malloc(bufferlen);
    CHECK_OOM(buffer_p)

    memset(buffer_p, 0, bufferlen);

    if (assoc_len) {
        unsigned char *temp = (unsigned char *) malloc(data_len + assoc_len);
        memcpy(temp, assoc, assoc_len);
        if (data_len) {
            memcpy(&temp[assoc_len], data, data_len);
        }
        data = temp;
        data_len += assoc_len;
    }

    struct iovec iov;
    iov.iov_base = data;
    iov.iov_len = data_len;

    msg.msg_control = buffer_p;
    msg.msg_controllen = bufferlen;
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    // Set encrypt/decrypt operation
    header = CMSG_FIRSTHDR(&msg);
    if (!header) {
        fputs("Corrupted ancillary data", stderr);
        return -1;
    }
    header->cmsg_level = SOL_ALG;
    header->cmsg_type = ALG_SET_OP;
    header->cmsg_len = CMSG_LEN(sizeof(*type));
    type = (uint32_t *) CMSG_DATA(header);
    *type = enc;

    // Set IV
    if (ivsize) {
        header = CMSG_NXTHDR(&msg, header);
        if (!header) {
            fputs("Corrupted ancillary data", stderr);
            return -1;
        }
        header->cmsg_level = SOL_ALG;
        header->cmsg_type = ALG_SET_IV;
        header->cmsg_len = iv_msg_size;
        alg_iv = (struct af_alg_iv *) CMSG_DATA(header);
        alg_iv->ivlen = ivsize;
        memcpy(alg_iv->iv, iv, ivsize);
    }

    // Set associated data length
    if (assoc_len) {
        header = CMSG_NXTHDR(&msg, header);
        if (!header) {
            fputs("Corrupted ancillary data", stderr);
            return -1;
        }
        header->cmsg_level = SOL_ALG;
        header->cmsg_type = ALG_SET_AEAD_ASSOCLEN;
        header->cmsg_len = CMSG_LEN(sizeof(*assoclen));
        assoclen = (uint32_t *) CMSG_DATA(header);
        *assoclen = (uint32_t) assoc_len;
    }

    if (sendmsg(op, &msg, 0) < 0) {
        fprintf(stderr, "set cipher parameters failed: %d\n", errno);
        return -1;
    }

    free(buffer_p);
    if (assoc_len) {
        free(data);
    }
    return 0;
}