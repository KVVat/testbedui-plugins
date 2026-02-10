#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <linux/if_alg.h>
#include <sys/socket.h>

#include "alg_common.h"
#include "message.h"
#include "util.h"

void af_alg_aes_multi(message *msg, char *cipher_name, int encrypt) {
    // message format: algorithm name, key, data, iv, iterations

    int iterations = 0;
    if (msg->n == 4) {
        iterations = 1;
    } else if (msg->n == 5) {
        iterations = *(int *) msg->buffers[4];
    } else {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    char *result = (char *) malloc(msg->data_lens[2]);
    CHECK_OOM(result)
    ssize_t result_len = 0;

    unsigned char *input = msg->buffers[2];
    unsigned char *prev_input = (unsigned char *) malloc(msg->data_lens[2]);
    unsigned char *prev_result = (unsigned char *) malloc(msg->data_lens[2]);
    unsigned char *iv = msg->buffers[3];

    for (int i = 0; i < iterations; i++) {
        if (result_len) {
            memcpy(prev_result, result, result_len);
        }
        if (i > 0) {
            if (encrypt) {
                memcpy(iv, result, msg->data_lens[3]);
            } else {
                memcpy(iv, prev_input, msg->data_lens[3]);
            }
        }

        int algo = create_algo_socket("skcipher", cipher_name);
        if (algo == -1) {
            debug_print(msg);
            exit(1);
        }

        setsockopt(algo, SOL_ALG, ALG_SET_KEY, msg->buffers[1], msg->data_lens[1]);

        int op = accept(algo, 0, 0);

        int temp = af_alg_cipher_parameters(op, encrypt ? ALG_OP_ENCRYPT
                                                        : ALG_OP_DECRYPT, input,
                                            msg->data_lens[2], iv,
                                            msg->data_lens[3], NULL, 0);
        if (temp == -1) {
            debug_print(msg);
            exit(1);
        }

        result_len = read(op, result, msg->data_lens[2]);
        if (result_len == -1) {
            fprintf(stderr, "%s - failed: %d\n", msg->buffers[0], errno);
            debug_print(msg);
            exit(1);
        }

        destroy_algo_socket(algo);
        close(op);

        if (!encrypt) {
            memcpy(prev_input, input, msg->data_lens[3]);
        }

        if (i == 0) {
            memcpy(input, iv, msg->data_lens[3]);
        } else {
            memcpy(input, prev_result, msg->data_lens[3]);
        }
    }

    message *response;
    if (msg->n == 5) {
        response = create_message(2);
        add_buffer(response, result, result_len);
        add_buffer(response, (char *) prev_result, msg->data_lens[3]);
    } else {
        response = create_message(1);
        add_buffer(response, result, result_len);
    }

    message_to_stdout(response);

    free(response);
    free(result);
}

// TODO: maybe merge this with af_alg_aes_multi
void af_alg_aes_ecb(message *msg, char *cipher_name, int encrypt) {
    // message format: algorithm name, key, data, iterations

    int iterations = 0;
    if (msg->n == 3) {
        iterations = 1;
    } else if (msg->n == 4) {
        iterations = *(int *) msg->buffers[3];
    } else {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    unsigned char *result = (unsigned char *) malloc(msg->data_lens[2]);
    memcpy(result, msg->buffers[2], msg->data_lens[2]);
    CHECK_OOM(result)
    ssize_t result_len = 0;

    unsigned char *prev_result = (unsigned char *) malloc(msg->data_lens[2]);

    for (int i = 0; i < iterations; i++) {
        if (i == iterations - 1) {
            memcpy(prev_result, result, result_len);
        }

        int algo = create_algo_socket("skcipher", cipher_name);
        if (algo == -1) {
            debug_print(msg);
            exit(1);
        }

        setsockopt(algo, SOL_ALG, ALG_SET_KEY, msg->buffers[1], msg->data_lens[1]);

        int op = accept(algo, 0, 0);

        int temp = af_alg_cipher_parameters(op, encrypt ? ALG_OP_ENCRYPT
                                                        : ALG_OP_DECRYPT, result,
                                            msg->data_lens[2],
                                            NULL, 0, NULL, 0);
        if (temp == -1) {
            debug_print(msg);
            exit(1);
        }

        result_len = read(op, result, msg->data_lens[2]);
        if (result_len == -1) {
            fprintf(stderr, "%s - failed: %d\n", msg->buffers[0], errno);
            debug_print(msg);
            exit(1);
        }

        destroy_algo_socket(algo);
        close(op);
    }

    message *response;
    response = create_message(2);
    add_buffer(response, (char *) result, result_len);
    add_buffer(response, (char *) prev_result, msg->data_lens[2]);

    message_to_stdout(response);

    free(response);
    free(result);
}

void af_alg_aes_single(message *msg, char *cipher_name, int encrypt) {
    // message format: algorithm name, key, data, iv, 1

    if (msg->n != 5) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    char *result = (char *) malloc(msg->data_lens[2]);
    CHECK_OOM(result)
    ssize_t result_len = 0;

    int algo = create_algo_socket("skcipher", cipher_name);
    if (algo == -1) {
        debug_print(msg);
        exit(1);
    }

    setsockopt(algo, SOL_ALG, ALG_SET_KEY, msg->buffers[1], msg->data_lens[1]);

    int op = accept(algo, 0, 0);

    int temp = af_alg_cipher_parameters(op,
                                        encrypt ? ALG_OP_ENCRYPT : ALG_OP_DECRYPT,
                                        msg->buffers[2],
                                        msg->data_lens[2], msg->buffers[3],
                                        msg->data_lens[3], NULL, 0);
    if (temp == -1) {
        debug_print(msg);
        exit(1);
    }

    result_len = read(op, result, msg->data_lens[2]);
    if (result_len == -1) {
        fprintf(stderr, "%s - failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    destroy_algo_socket(algo);
    close(op);

    message *response;
    response = create_message(1);
    add_buffer(response, result, result_len);

    message_to_stdout(response);

    free(response);
    free(result);
}