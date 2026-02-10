#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "util.h"

char **read_af_alg_config() {
    char **af_alg_config = (char **) calloc(AF_ALG_ALGO_COUNT, sizeof(char *));
    CHECK_OOM(af_alg_config)

    char path[1024];
    if (readlink("/proc/self/exe", path, 1024) == -1) {
        fputs("Unable to get own path", stderr);
        exit(1);
    }
    char *pos = strrchr(path, '/');
    *(pos+1) = '\x00';
    strcat(path, "af_alg_config.txt");

    FILE *f = fopen(path, "r");
    if (f == NULL) {
        fprintf(stderr, "Unable to open %s\n", path);
        exit(1);
    }

    for (int i = 0; i < AF_ALG_ALGO_COUNT; i++) {
        size_t buf_size = 0;
        ssize_t result = getline(&af_alg_config[i], &buf_size, f);
        if (result == -1) {
            fprintf(stderr, "Unable to read %s: %d\n", path, i);
            exit(1);
        }
        af_alg_config[i][result - 1] = '\x00';
    }

    fclose(f);
    return af_alg_config;
}