#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <linux/if_alg.h>
#include <sys/socket.h>

#include "alg_common.h"
#include "message.h"
#include "util.h"

void af_alg_drbg_send(int op, unsigned char *buffer, uint32_t len) {
    struct iovec iov;

    iov.iov_base = (void *) (uintptr_t) buffer;
    iov.iov_len = len;

    struct msghdr msg;

    msg.msg_name = NULL;
    msg.msg_namelen = 0;
    msg.msg_control = NULL;
    msg.msg_controllen = 0;
    msg.msg_flags = 0;
    msg.msg_iov = &iov;
    msg.msg_iovlen = 1;

    sendmsg(op, &msg, 0);
}

void af_alg_drbg_recv(int op, unsigned char *buffer, uint32_t len) {
    struct iovec iov;

    while (len) {
        int32_t r = 0;

        iov.iov_base = (void *) (uintptr_t) buffer;
        iov.iov_len = len;

        struct msghdr msg;

        msg.msg_name = NULL;
        msg.msg_namelen = 0;
        msg.msg_control = NULL;
        msg.msg_controllen = 0;
        msg.msg_flags = 0;
        msg.msg_iov = &iov;
        msg.msg_iovlen = 1;

        r = recvmsg(op, &msg, 0);
        len -= r;
        buffer += r;
    }
}

void af_alg_ctrdrbg(message *msg) {
    // message format: algorithm name, out_len_bytes, entropy, personalisation,
    //                 additional_data1, additional_data2, nonce

    if (msg->n != 7) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    int num_bytes = *(int *) msg->buffers[1];
    unsigned char *buf = (unsigned char *) malloc(num_bytes);

    int algo = create_algo_socket("rng", "drbg_nopr_ctr_aes256");
    if (algo == -1) {
        debug_print(msg);
        exit(1);
    }

    if (setsockopt(algo, SOL_ALG, ALG_SET_DRBG_ENTROPY, msg->buffers[2],
                   msg->data_lens[2]) == -1) {
        fprintf(stderr, "%s - Unable to set entropy: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }
    if (setsockopt(algo, SOL_ALG, ALG_SET_KEY, msg->buffers[3],
                   msg->data_lens[3]) == -1) {
        fprintf(stderr, "%s - Unable to set personalisation data: %d\n",
                msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    int op = accept(algo, 0, 0);

    af_alg_drbg_send(op, msg->buffers[4], msg->data_lens[4]);
    af_alg_drbg_recv(op, buf, num_bytes);
    af_alg_drbg_send(op, msg->buffers[5], msg->data_lens[5]);
    af_alg_drbg_recv(op, buf, num_bytes);

    destroy_algo_socket(algo);
    close(op);

    message *response;
    response = create_message(1);
    add_buffer(response, (char *) buf, num_bytes);
    debug_print(response);
    message_to_stdout(response);

    free(response);
    free(buf);
}

void af_alg_hmacdrbg(message *msg, char *mode) {
    // message format: algorithm name, out_len_bytes, entropy, personalisation,
    //                 additional_data1, additional_data2, nonce

    if (msg->n != 7) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    int num_bytes = *(int *) msg->buffers[1];
    unsigned char *buf = (unsigned char *) malloc(num_bytes);

    int algo = create_algo_socket("rng", mode);
    if (algo == -1) {
        debug_print(msg);
        exit(1);
    }

    char *entropy = (char *) malloc(msg->data_lens[2] + msg->data_lens[6]);
    CHECK_OOM(entropy)

    memcpy(entropy, msg->buffers[2], msg->data_lens[2]);
    memcpy(entropy + msg->data_lens[2], msg->buffers[6], msg->data_lens[6]);

    if (setsockopt(algo, SOL_ALG, ALG_SET_DRBG_ENTROPY, entropy,
                   msg->data_lens[2] + msg->data_lens[6]) == -1) {
        fprintf(stderr, "%s - Unable to set entropy: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }
    if (setsockopt(algo, SOL_ALG, ALG_SET_KEY, msg->buffers[3],
                   msg->data_lens[3]) == -1) {
        fprintf(stderr, "%s - Unable to set personalisation data: %d\n",
                msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    int op = accept(algo, 0, 0);

    af_alg_drbg_send(op, msg->buffers[4], msg->data_lens[4]);
    af_alg_drbg_recv(op, buf, num_bytes);
    af_alg_drbg_send(op, msg->buffers[5], msg->data_lens[5]);
    af_alg_drbg_recv(op, buf, num_bytes);

    destroy_algo_socket(algo);
    close(op);

    message *response;
    response = create_message(1);
    add_buffer(response, (char *) buf, num_bytes);
    message_to_stdout(response);

    free(response);
    free(buf);
}