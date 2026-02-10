#ifndef EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_MESSAGE_H_
#define EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_MESSAGE_H_

typedef struct message_s {
    int n;
    int i;
    int *data_lens;
    unsigned char **buffers;
} message;

message *create_message(int n);
void free_message(message *msg);

message *message_from_stdin();
int is_command(message *msg, char const *command);

void add_buffer(message *msg, char *buffer, int len);
void message_to_stdout(message *msg);

void debug_print(message *msg);

#endif  // EXPERIMENTAL_USERS_ECX_ACVP_KERNEL_HARNESS_MESSAGE_H_