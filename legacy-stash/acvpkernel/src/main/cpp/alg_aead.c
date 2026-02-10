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

void af_alg_aead(message *msg, char *cipher_name, int encrypt) {
    // message format: algorithm name, tag_len, key, data, nonce, ad

    if (msg->n != 6) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }

    int algo = create_algo_socket("aead", cipher_name);
    if (algo == -1) {
        debug_print(msg);
        exit(1);
    }

    setsockopt(algo, SOL_ALG, ALG_SET_KEY, msg->buffers[2], msg->data_lens[2]);
    setsockopt(algo, SOL_ALG, ALG_SET_AEAD_AUTHSIZE, NULL,
               *((int *) msg->buffers[1]));

    int op = accept(algo, 0, 0);

    unsigned char *iv;
    int iv_len;
    if (strcmp(cipher_name, "ccm(aes)") == 0) {
        iv = (unsigned char *) malloc(16);
        memset(iv, 0, 16);
        iv[0] = 14 - msg->data_lens[4];
        memcpy(&iv[1], msg->buffers[4], msg->data_lens[4]);
        iv_len = 16;
    } else {
        iv = msg->buffers[4];
        iv_len = msg->data_lens[4];
    }

    unsigned char *input;
    int input_len;
    if (encrypt) {
        input_len = msg->data_lens[3] + *((int *) msg->buffers[1]);
        input = (unsigned char *) malloc(input_len);
        memset(input, 0, input_len);
        memcpy(input, msg->buffers[3], msg->data_lens[3]);
    } else {
        input = msg->buffers[3];
        input_len = msg->data_lens[3];
    }

    int temp = af_alg_cipher_parameters(op,
                                        encrypt ? ALG_OP_ENCRYPT : ALG_OP_DECRYPT,
                                        input, input_len, iv, iv_len,
                                        msg->buffers[5], msg->data_lens[5]);
    if (temp == -1) {
        debug_print(msg);
        exit(1);
    }

    char *result;
    ssize_t result_len;
    char success[1];

    if (encrypt) {
        result = (char *) malloc(input_len + msg->data_lens[5]);
        CHECK_OOM(result)

        result_len = read(op, result, input_len + msg->data_lens[5]);
        if (result_len == -1) {
            fprintf(stderr, "%s - Unable to encrypt: %d\n", msg->buffers[0], errno);
            debug_print(msg);
            exit(1);
        }
    } else {
        int temp = input_len + msg->data_lens[5] - *((int *) msg->buffers[1]);
        result = (char *) malloc(temp);
        CHECK_OOM(result)

        result_len = read(op, result, temp);
        if (result_len == -1) {
            success[0] = 0;
        } else {
            success[0] = 1;
        }
    }

    destroy_algo_socket(algo);
    close(op);

    message *response;
    if (encrypt) {
        response = create_message(1);
        add_buffer(response, result + msg->data_lens[5],
                   result_len - msg->data_lens[5]);
        message_to_stdout(response);
    } else {
        response = create_message(2);
        add_buffer(response, success, 1);
        if (success[0]) {
            add_buffer(response, result + msg->data_lens[5],
                       result_len - msg->data_lens[5]);
        } else {
            add_buffer(response, NULL, 0);
        }

        message_to_stdout(response);
    }

    free(response);
    free(result);
}