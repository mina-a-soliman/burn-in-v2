#pragma once

#ifdef __cplusplus
extern "C" {
#endif

typedef struct BurnJavaCallbacks {
    void *vm;
    void *engine_global;
} BurnJavaCallbacks;

void burn_set_java_callbacks(void *vm, void *engine_global);
void burn_clear_java_callbacks(void);
void burn_forward_log(const char *line);
void burn_forward_progress(long time_ms);

int burn_subtitles(
        const char *input_path,
        const char *output_path,
        const char *ass_path,
        const char *fonts_dir,
        const char *fontconfig_path,
        int crf,
        const char *preset,
        long duration_ms);

void burn_request_cancel(void);
int burn_is_cancelled(void);

#ifdef __cplusplus
}
#endif
