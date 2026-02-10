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

void af_alg_hash(message *msg, char *hash_name) {
    // message format: algorithm name, data to hash

    if (msg->n != 2) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    int algo = create_algo_socket("hash", hash_name);
    if (algo == -1) {
        debug_print(msg);
        exit(1);
    }

    int op = accept(algo, 0, 0);
    if (write(op, msg->buffers[1], msg->data_lens[1]) != msg->data_lens[1]) {
        fprintf(stderr, "%s - write failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    char *result = (char *) malloc(512);
    CHECK_OOM(result)

    ssize_t result_len = read(op, result, 512);
    if (result_len == -1) {
        fprintf(stderr, "%s - read failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    destroy_algo_socket(algo);
    close(op);

    message *response = create_message(1);
    add_buffer(response, result, result_len);
    message_to_stdout(response);

    free(response);
    free(result);
}

void af_alg_hash_mct(message *msg, char *hash_name) {
    // message format: algorithm name, initial_seed

    if (msg->n != 2) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    char *input = malloc(msg->data_lens[1] * 3);
    CHECK_OOM(input)
    memcpy(input, msg->buffers[1], msg->data_lens[1]);
    memcpy(input + msg->data_lens[1], msg->buffers[1], msg->data_lens[1]);
    memcpy(input + msg->data_lens[1] * 2, msg->buffers[1], msg->data_lens[1]);

    ssize_t result_len;
    char *result = (char *) malloc(512);
    CHECK_OOM(result)

    for (int i = 0; i < 1000; i++) {
        int algo = create_algo_socket("hash", hash_name);
        if (algo == -1) {
            debug_print(msg);
            exit(1);
        }

        int op = accept(algo, 0, 0);
        if (write(op, input, msg->data_lens[1] * 3) != msg->data_lens[1] * 3) {
            fprintf(stderr, "%s - write failed: %d\n", msg->buffers[0], errno);
            debug_print(msg);
            exit(1);
        }

        result_len = read(op, result, 512);
        if (result_len == -1) {
            fprintf(stderr, "%s - read failed: %d\n", msg->buffers[0], errno);
            debug_print(msg);
            exit(1);
        }

        destroy_algo_socket(algo);
        close(op);

        memmove(input, input + msg->data_lens[1], msg->data_lens[1] * 2);
        memcpy(input + msg->data_lens[1] * 2, result, result_len);
    }

    message *response = create_message(1);
    add_buffer(response, result, result_len);
    message_to_stdout(response);

    free(response);
    free(result);
}

void af_alg_hmac(message *msg, char *hash_name) {
    // message format: algorithm name, data to sign, key

    if (msg->n != 3) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    int algo = create_algo_socket("hash", hash_name);
    if (algo == -1) {
        debug_print(msg);
        exit(1);
    }

    if (setsockopt(algo, SOL_ALG, ALG_SET_KEY,
                   msg->buffers[2], msg->data_lens[2]) == -1) {
        fprintf(stderr, "%s - setsockopt failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    int op = accept(algo, 0, 0);
    if (write(op, msg->buffers[1], msg->data_lens[1]) != msg->data_lens[1]) {
        fprintf(stderr, "%s - write failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    char *result = (char *) malloc(512);
    CHECK_OOM(result)

    ssize_t result_len = read(op, result, 512);
    if (result_len == -1) {
        fprintf(stderr, "%s - read failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    destroy_algo_socket(algo);
    close(op);

    message *response = create_message(1);
    add_buffer(response, result, result_len);
    message_to_stdout(response);

    free(response);
    free(result);
}

void af_alg_cmac(message *msg, char *hash_name, int verify) {
    // sign message format: algorithm name, number of bytes, key, message
    // verify message format: algorithm name, key, message, claimed mac

    if (msg->n != 4) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    int algo = create_algo_socket("hash", hash_name);
    if (algo == -1) {
        debug_print(msg);
        exit(1);
    }

    unsigned char *key;
    unsigned char *data;
    size_t key_len;
    size_t data_len;
    if (verify) {
        key = msg->buffers[1];
        key_len = msg->data_lens[1];
        data = msg->buffers[2];
        data_len = msg->data_lens[2];
    } else {
        key = msg->buffers[2];
        key_len = msg->data_lens[2];
        data = msg->buffers[3];
        data_len = msg->data_lens[3];
    }

    if (setsockopt(algo, SOL_ALG, ALG_SET_KEY, key, key_len) == -1) {
        fprintf(stderr, "%s - setsockopt failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    int op = accept(algo, 0, 0);
    if (write(op, data, data_len) != data_len) {
        fprintf(stderr, "%s - write failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    char *result = (char *) malloc(512);
    CHECK_OOM(result)

    ssize_t result_len = read(op, result, 512);
    if (result_len == -1) {
        fprintf(stderr, "%s - read failed: %d\n", msg->buffers[0], errno);
        debug_print(msg);
        exit(1);
    }

    destroy_algo_socket(algo);
    close(op);

    message *response = create_message(1);
    if (verify) {
        if (result_len < msg->data_lens[3]) {
            fprintf(stderr, "%s - claimed mac is too long: %d vs %zd\n",
                    msg->buffers[0], msg->data_lens[3], result_len);
            debug_print(msg);
            exit(1);
        }
        char success[1];
        success[0] = 0;
        if (!memcmp(result, msg->buffers[3], msg->data_lens[3])) {
            success[0] = 1;
        }
        add_buffer(response, success, 1);
    } else {
        int mac_len = *(int *)msg->buffers[1];
        if (result_len < mac_len) {
            fprintf(stderr, "%s - requested mac is too long: %d vs %zd\n",
                    msg->buffers[0], mac_len, result_len);
            debug_print(msg);
            exit(1);
        }
        add_buffer(response, result, mac_len);
    }
    message_to_stdout(response);

    free(response);
    free(result);
}