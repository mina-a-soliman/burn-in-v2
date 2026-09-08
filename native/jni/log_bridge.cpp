#include "log_bridge.h"

void burn_log_command(int argc, char **argv) {
    for (int i = 0; i < argc; ++i) {
        BURN_LOGI("argv[%d]=%s", i, argv[i] ? argv[i] : "(null)");
    }
}
