#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "message.h"
#include "util.h"
#include <assert.h>

/* ======================= LOW LEVEL ACVPTOOL PROTOCOL ====================== */

void free_message(message *msg) {
    for (int i = 0; i < msg->n; i++) {
        free(msg->buffers[i]);
    }
    free(msg->buffers);
    free(msg->data_lens);
    free(msg);
}

message *create_message(int n) {
    message *msg = (message *) malloc(sizeof(message));
    CHECK_OOM(msg)

    msg->n = n;
    msg->i = 0;

    msg->data_lens = (int *) malloc(sizeof(int) * msg->n);
    CHECK_OOM(msg->data_lens)

    msg->buffers = (unsigned char **) malloc(sizeof(unsigned char *) * msg->n);
    CHECK_OOM(msg->buffers)

    return msg;
}

void debug_print(message *msg) {
    for (int i = 0; i < msg->n; i++) {
        for (int k = 0; k < msg->data_lens[i]; k++) {
            fprintf(stderr, "%02x", msg->buffers[i][k]);
        }
        fprintf(stderr, "\n");
    }
    fprintf(stderr, "\n");
}

message *message_from_stdin() {
    int n;
    if (fread(&n, sizeof(n), 1, stdin) != 1) {
        //__android_log_write(ANDROID_LOG_DEBUG, "Acvptool", "no stdin exit");
        exit(1);
    }

    message *msg = create_message(n);

    if (fread(msg->data_lens, sizeof(int), msg->n, stdin) != msg->n) {
        exit(1);
    }

    for (int i = 0; i < n; i++) {
        if (msg->data_lens[i]) {
            msg->buffers[i] = (unsigned char *) malloc(msg->data_lens[i]);
            CHECK_OOM(msg->buffers[i])

            if (fread(msg->buffers[i], msg->data_lens[i], 1, stdin) != 1) {
                exit(1);
            }
        } else {
            msg->buffers[i] = 0;
        }
    }

    return msg;
}

void message_to_stdout(message *msg) {
    if (fwrite(&msg->n, sizeof(msg->n), 1, stdout) != 1) {
        exit(1);
    }
    if (fwrite(msg->data_lens, sizeof(int), msg->n, stdout) != msg->n) {
        exit(1);
    }
    for (int i = 0; i < msg->n; i++) {
        if (msg->data_lens[i]) {
            if (fwrite(msg->buffers[i], msg->data_lens[i], 1, stdout) != 1) {
                exit(1);
            }
        }
    }
    fflush(stdout);
}

void add_buffer(message *msg, char *buffer, int len) {
    msg->data_lens[msg->i] = len;
    if (len) {
        msg->buffers[msg->i] = (unsigned char *) malloc(msg->data_lens[msg->i]);
        CHECK_OOM(msg->buffers[msg->i])
        memcpy(msg->buffers[msg->i], buffer, len);
    } else {
        msg->buffers[msg->i] = 0;
    }
    msg->i += 1;
}

int is_command(message *msg, char const *command) {
    if (msg->data_lens[0] != strlen(command)) {
        return 0;
    }
    if (strncmp((const char *) msg->buffers[0], command, msg->data_lens[0])) {
        return 0;
    }
    return 1;
}