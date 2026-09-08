#pragma once

#include <android/log.h>

#define BURN_LOG_TAG "BurnFFmpeg"
#define BURN_LOGI(...) __android_log_print(ANDROID_LOG_INFO, BURN_LOG_TAG, __VA_ARGS__)
#define BURN_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, BURN_LOG_TAG, __VA_ARGS__)

#ifdef __cplusplus
extern "C" {
#endif

void burn_log_command(int argc, char **argv);

#ifdef __cplusplus
}
#endif
