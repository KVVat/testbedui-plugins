#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "message.h"
#include "alg_hash.h"
#include "alg_cipher.h"
#include "alg_aead.h"
#include "alg_drbg.h"
#include "util.h"


void get_config(message *msg) {
    if (msg->n != 1) {
        fprintf(stderr, "Malformed message received: %s\n", msg->buffers[0]);
        debug_print(msg);
        exit(1);
    }
    message *response = create_message(1);
    static char config[] = "[{\"algorithm\": \"SHA2-224\", \"revision\": \"1.0\", \"messageLength\": [{\"min\": 0, \"max\": 65528, \"increment\": 8}]}, {\"algorithm\": \"SHA2-256\", \"revision\": \"1.0\", \"messageLength\": [{\"min\": 0, \"max\": 65528, \"increment\": 8}]}, {\"algorithm\": \"SHA2-384\", \"revision\": \"1.0\", \"messageLength\": [{\"min\": 0, \"max\": 65528, \"increment\": 8}]}, {\"algorithm\": \"SHA2-512\", \"revision\": \"1.0\", \"messageLength\": [{\"min\": 0, \"max\": 65528, \"increment\": 8}]}, {\"algorithm\": \"SHA-1\", \"revision\": \"1.0\", \"messageLength\": [{\"min\": 0, \"max\": 65528, \"increment\": 8}]}, {\"algorithm\": \"ACVP-AES-CBC\", \"revision\": \"1.0\", \"direction\": [\"encrypt\", \"decrypt\"], \"keyLen\": [128, 192, 256]}, {\"algorithm\": \"ACVP-AES-CBC-CS3\", \"revision\": \"1.0\", \"direction\": [\"encrypt\", \"decrypt\"], \"keyLen\": [128, 192, 256]}, {\"algorithm\": \"ACVP-AES-ECB\", \"revision\": \"1.0\", \"direction\": [\"encrypt\", \"decrypt\"], \"keyLen\": [128, 192, 256]}, {\"algorithm\": \"ACVP-AES-CTR\", \"revision\": \"1.0\", \"direction\": [\"encrypt\", \"decrypt\"], \"keyLen\": [128, 192, 256], \"payloadLen\": [{\"min\": 8, \"max\": 128, \"increment\": 8}], \"incrementalCounter\": true, \"overflowCounter\": true, \"performCounterTests\": true}, {\"algorithm\": \"ACVP-AES-GCM\", \"revision\": \"1.0\", \"direction\": [\"encrypt\", \"decrypt\"], \"keyLen\": [128, 192, 256], \"payloadLen\": [{\"min\": 0, \"max\": 256, \"increment\": 8}], \"aadLen\": [{\"min\": 0, \"max\": 320, \"increment\": 8}], \"tagLen\": [32, 64, 96, 104, 112, 120, 128], \"ivLen\": [96], \"ivGen\": \"external\"}, {\"algorithm\": \"HMAC-SHA-1\", \"revision\": \"1.0\", \"keyLen\": [{\"min\": 8, \"max\": 2048, \"increment\": 8}], \"macLen\": [{\"min\": 32, \"max\": 160, \"increment\": 8}]}, {\"algorithm\": \"HMAC-SHA2-224\", \"revision\": \"1.0\", \"keyLen\": [{\"min\": 8, \"max\": 2048, \"increment\": 8}], \"macLen\": [{\"min\": 32, \"max\": 224, \"increment\": 8}]}, {\"algorithm\": \"HMAC-SHA2-256\", \"revision\": \"1.0\", \"keyLen\": [{\"min\": 8, \"max\": 2048, \"increment\": 8}], \"macLen\": [{\"min\": 32, \"max\": 256, \"increment\": 8}]}, {\"algorithm\": \"HMAC-SHA2-384\", \"revision\": \"1.0\", \"keyLen\": [{\"min\": 8, \"max\": 2048, \"increment\": 8}], \"macLen\": [{\"min\": 32, \"max\": 384, \"increment\": 8}]}, {\"algorithm\": \"HMAC-SHA2-512\", \"revision\": \"1.0\", \"keyLen\": [{\"min\": 8, \"max\": 2048, \"increment\": 8}], \"macLen\": [{\"min\": 32, \"max\": 512, \"increment\": 8}]}, {\"algorithm\": \"ACVP-AES-XTS\", \"revision\": \"1.0\", \"direction\": [\"encrypt\", \"decrypt\"], \"keyLen\": [128, 192, 256]}, {\"algorithm\": \"CMAC-AES\", \"revision\": \"1.0\", \"capabilities\": [{\"direction\": [\"gen\"], \"msgLen\": [{\"min\": 0, \"max\": 65536, \"increment\": 8}], \"keyLen\": [128, 256], \"macLen\": [{\"min\": 32, \"max\": 128, \"increment\": 8}]}]}, {\"algorithm\": \"hmacDRBG\", \"revision\": \"1.0\", \"predResistanceEnabled\": [false], \"reseedImplemented\": false, \"capabilities\": [{\"mode\": [\"SHA-1\", \"SHA2-224\", \"SHA2-256\", \"SHA2-384\", \"SHA2-512\"], \"derFuncEnabled\": false, \"entropyInputLen\": [160, 224, 256, 384, 512], \"nonceLen\": [64, 128], \"persoStringLen\": [128, 256], \"additionalInputLen\": [128, 256], \"returnedBitsLen\": [480, 864, 896, 1024, 1152]}]}]";
    add_buffer(response, config, strlen(config));
    message_to_stdout(response);
    free_message(response);
}

int main() {
    char **impl = read_af_alg_config();
    //__android_log_write(ANDROID_LOG_DEBUG, "Acvptool", "Launched!");
    while (1) {
        message *msg = message_from_stdin();
        //__android_log_write(ANDROID_LOG_DEBUG, "Acvptool2", msg->buffers[0]);
        if (msg->n < 1) {
            fputs("Malformed message received", stderr);
            exit(1);
        }
        //__android_log_write(ANDROID_LOG_DEBUG, "Acvptool3", msg->buffers[0]);
        if (is_command(msg, "getConfig")) {
            get_config(msg);
        } else if (is_command(msg, "SHA-1")) {
            af_alg_hash(msg, impl[SHA1]);
        } else if (is_command(msg, "SHA2-224")) {
            af_alg_hash(msg, impl[SHA224]);
        } else if (is_command(msg, "SHA2-256")) {
            af_alg_hash(msg, impl[SHA256]);
        } else if (is_command(msg, "SHA2-384")) {
            af_alg_hash(msg, impl[SHA384]);
        } else if (is_command(msg, "SHA2-512")) {
            af_alg_hash(msg, impl[SHA512]);
        } else if (is_command(msg, "SHA-1/MCT")) {
            af_alg_hash_mct(msg, impl[SHA1]);
        } else if (is_command(msg, "SHA2-224/MCT")) {
            af_alg_hash_mct(msg, impl[SHA224]);
        } else if (is_command(msg, "SHA2-256/MCT")) {
            af_alg_hash_mct(msg, impl[SHA256]);
        } else if (is_command(msg, "SHA2-384/MCT")) {
            af_alg_hash_mct(msg, impl[SHA384]);
        } else if (is_command(msg, "SHA2-512/MCT")) {
            af_alg_hash_mct(msg, impl[SHA512]);
        } else if (is_command(msg, "HMAC-SHA-1")) {
            af_alg_hmac(msg, impl[HMAC_SHA1]);
        } else if (is_command(msg, "HMAC-SHA2-224")) {
            af_alg_hmac(msg, impl[HMAC_SHA224]);
        } else if (is_command(msg, "HMAC-SHA2-256")) {
            af_alg_hmac(msg, impl[HMAC_SHA256]);
        } else if (is_command(msg, "HMAC-SHA2-384")) {
            af_alg_hmac(msg, impl[HMAC_SHA384]);
        } else if (is_command(msg, "HMAC-SHA2-512")) {
            af_alg_hmac(msg, impl[HMAC_SHA512]);
        } else if (is_command(msg, "AES/encrypt")) {
            af_alg_aes_ecb(msg, impl[ECB_AES], 1);
        } else if (is_command(msg, "AES/decrypt")) {
            af_alg_aes_ecb(msg, impl[ECB_AES], 0);
        } else if (is_command(msg, "AES-CBC/encrypt")) {
            af_alg_aes_multi(msg, impl[CBC_AES], 1);
        } else if (is_command(msg, "AES-CBC/decrypt")) {
            af_alg_aes_multi(msg, impl[CBC_AES], 0);
        } else if (is_command(msg, "AES-CTR/encrypt")) {
            af_alg_aes_single(msg, impl[CTR_AES], 1);
        } else if (is_command(msg, "AES-CTR/decrypt")) {
            af_alg_aes_single(msg, impl[CTR_AES], 0);
        } else if (is_command(msg, "AES-XTS/encrypt")) {
            af_alg_aes_multi(msg, impl[XTS_AES], 1);
        } else if (is_command(msg, "AES-XTS/decrypt")) {
            af_alg_aes_multi(msg, impl[XTS_AES], 0);
        } else if (is_command(msg, "AES-GCM/seal")) {
            af_alg_aead(msg, impl[GCM_AES], 1);
        } else if (is_command(msg, "AES-GCM/open")) {
            af_alg_aead(msg, impl[GCM_AES], 0);
        } else if (is_command(msg, "AES-CBC-CS3/encrypt")) {
            af_alg_aes_single(msg, impl[CTS_CBC_AES], 1);
        } else if (is_command(msg, "AES-CBC-CS3/decrypt")) {
            af_alg_aes_single(msg, impl[CTS_CBC_AES], 0);
        } else if (is_command(msg, "hmacDRBG/SHA-1")) {
            af_alg_hmacdrbg(msg, impl[DRBG_HMAC_SHA1]);
        } else if (is_command(msg, "hmacDRBG/SHA2-224")) {
            af_alg_hmacdrbg(msg, impl[DRBG_HMAC_SHA224]);
        } else if (is_command(msg, "hmacDRBG/SHA2-256")) {
            af_alg_hmacdrbg(msg, impl[DRBG_HMAC_SHA256]);
        } else if (is_command(msg, "hmacDRBG/SHA2-384")) {
            af_alg_hmacdrbg(msg, impl[DRBG_HMAC_SHA384]);
        } else if (is_command(msg, "hmacDRBG/SHA2-512")) {
            af_alg_hmacdrbg(msg, impl[DRBG_HMAC_SHA512]);
        } else if (is_command(msg, "CMAC-AES/verify")) {
            af_alg_cmac(msg, impl[CMAC_AES], 1);
        } else if (is_command(msg, "CMAC-AES")) {
            af_alg_cmac(msg, impl[CMAC_AES], 0);
            // disabled commands
            // } else if (is_command(msg, "AES-CCM/seal")) {
            //   af_alg_aead(msg, "ccm(aes)", 1);
            // } else if (is_command(msg, "AES-CCM/open")) {
            //   af_alg_aead(msg, "ccm(aes)", 0);
            // } else if (is_command(msg, "ctrDRBG/AES-256")) {
            //   af_alg_ctrdrbg(msg);
        } else {
            fprintf(stderr, "Received unknown message: %s\n", msg->buffers[0]);
            exit(1);
        }

        free_message(msg);
    }
}