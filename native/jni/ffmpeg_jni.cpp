#include "ffmpeg_burn.h"

#include "log_bridge.h"

#include <jni.h>

#include <atomic>
#include <cstdlib>
#include <cstring>
#include <mutex>

namespace {
std::atomic<bool> g_cancel{false};
JavaVM *g_vm = nullptr;
jobject g_engine = nullptr;
jmethodID g_on_log = nullptr;
jmethodID g_on_progress = nullptr;
std::mutex g_cb_mutex;

// FFmpeg logs and reports progress from its own worker threads (x264, filters).
// Those threads must not stay attached: ART aborts the process when a thread that
// never called DetachCurrentThread exits.
class ScopedEnv {
public:
    ScopedEnv() {
        if (g_vm == nullptr) {
            return;
        }
        const jint status = g_vm->GetEnv(reinterpret_cast<void **>(&env_), JNI_VERSION_1_6);
        if (status == JNI_OK) {
            return;
        }
        if (status == JNI_EDETACHED && g_vm->AttachCurrentThread(&env_, nullptr) == JNI_OK) {
            attached_ = true;
            return;
        }
        env_ = nullptr;
    }

    ~ScopedEnv() {
        if (attached_ && g_vm != nullptr) {
            g_vm->DetachCurrentThread();
        }
    }

    ScopedEnv(const ScopedEnv &) = delete;
    ScopedEnv &operator=(const ScopedEnv &) = delete;

    JNIEnv *get() const { return env_; }

private:
    JNIEnv *env_ = nullptr;
    bool attached_ = false;
};
}  // namespace

void burn_set_java_callbacks(void *vm, void *engine_global) {
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    {
        ScopedEnv scoped;
        if (scoped.get() != nullptr && g_engine != nullptr) {
            scoped.get()->DeleteGlobalRef(g_engine);
        }
    }
    g_vm = static_cast<JavaVM *>(vm);
    g_engine = static_cast<jobject>(engine_global);
    g_on_log = nullptr;
    g_on_progress = nullptr;
    ScopedEnv scoped;
    JNIEnv *env = scoped.get();
    if (env != nullptr && g_engine != nullptr) {
        jclass cls = env->GetObjectClass(g_engine);
        g_on_log = env->GetMethodID(cls, "onLogLine", "(Ljava/lang/String;)V");
        g_on_progress = env->GetMethodID(cls, "onNativeProgress", "(J)V");
        env->DeleteLocalRef(cls);
    }
}

void burn_clear_java_callbacks() {
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    ScopedEnv scoped;
    if (scoped.get() != nullptr && g_engine != nullptr) {
        scoped.get()->DeleteGlobalRef(g_engine);
    }
    g_engine = nullptr;
    g_on_log = nullptr;
    g_on_progress = nullptr;
}

void burn_forward_log(const char *line) {
    if (line == nullptr || line[0] == '\0') {
        return;
    }
    BURN_LOGI("%s", line);
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    if (g_engine == nullptr || g_on_log == nullptr) {
        return;
    }
    ScopedEnv scoped;
    JNIEnv *env = scoped.get();
    if (env == nullptr) {
        return;
    }
    jstring value = env->NewStringUTF(line);
    if (value == nullptr) {
        env->ExceptionClear();
        return;
    }
    env->CallVoidMethod(g_engine, g_on_log, value);
    env->DeleteLocalRef(value);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

void burn_forward_progress(long time_ms) {
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    if (g_engine == nullptr || g_on_progress == nullptr) {
        return;
    }
    ScopedEnv scoped;
    JNIEnv *env = scoped.get();
    if (env == nullptr) {
        return;
    }
    env->CallVoidMethod(g_engine, g_on_progress, static_cast<jlong>(time_ms));
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
}

void burn_request_cancel() {
    g_cancel.store(true);
}

int burn_is_cancelled() {
    return g_cancel.load() ? 1 : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_burnsubtitle_ffmpeg_FFmpegEngine_nativeInit(
        JNIEnv *env,
        jobject thiz,
        jstring fontConfigPath,
        jstring fontsDir) {
    JavaVM *vm = nullptr;
    env->GetJavaVM(&vm);
    jobject global = env->NewGlobalRef(thiz);
    burn_set_java_callbacks(vm, global);
#ifdef BURN_HAVE_FFMPEG
    const char *config = fontConfigPath != nullptr ? env->GetStringUTFChars(fontConfigPath, nullptr) : nullptr;
    const char *fonts = fontsDir != nullptr ? env->GetStringUTFChars(fontsDir, nullptr) : nullptr;
    if (config != nullptr) {
        setenv("FONTCONFIG_FILE", config, 1);
        BURN_LOGI("FONTCONFIG_FILE=%s", config);
    }
    if (fonts != nullptr) {
        setenv("FRIBIDI_NO_UTF8", "0", 1);
        BURN_LOGI("fontsdir=%s", fonts);
    }
    if (config != nullptr) env->ReleaseStringUTFChars(fontConfigPath, config);
    if (fonts != nullptr) env->ReleaseStringUTFChars(fontsDir, fonts);
    return 0;
#else
    (void) fontConfigPath;
    (void) fontsDir;
    BURN_LOGI("FFmpeg prebuilts are not linked; native burn is a stub in this build.");
    return 64;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_burnsubtitle_ffmpeg_FFmpegEngine_nativeBurn(
        JNIEnv *env,
        jobject /* thiz */,
        jstring inputPath,
        jstring outputPath,
        jstring assPath,
        jstring fontsDir,
        jstring fontConfigPath,
        jint crf,
        jstring preset,
        jlong durationMs) {
    if (burn_is_cancelled()) {
        return 255;
    }
    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    const char *output = env->GetStringUTFChars(outputPath, nullptr);
    const char *ass = env->GetStringUTFChars(assPath, nullptr);
    const char *fonts = env->GetStringUTFChars(fontsDir, nullptr);
    const char *config = fontConfigPath != nullptr ? env->GetStringUTFChars(fontConfigPath, nullptr) : nullptr;
    const char *preset_chars = preset != nullptr ? env->GetStringUTFChars(preset, nullptr) : "veryfast";
#ifdef BURN_HAVE_FFMPEG
    const int result = burn_subtitles(
            input,
            output,
            ass,
            fonts,
            config,
            static_cast<int>(crf),
            preset_chars,
            static_cast<long>(durationMs));
#else
    BURN_LOGE("Cannot burn subtitles: FFmpeg was not compiled into this APK.");
    burn_forward_log("FFmpeg libraries are missing from this APK build.");
    const int result = 64;
#endif
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    env->ReleaseStringUTFChars(assPath, ass);
    env->ReleaseStringUTFChars(fontsDir, fonts);
    if (fontConfigPath != nullptr && config != nullptr) {
        env->ReleaseStringUTFChars(fontConfigPath, config);
    }
    if (preset != nullptr) {
        env->ReleaseStringUTFChars(preset, preset_chars);
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_burnsubtitle_ffmpeg_FFmpegEngine_nativeCancel(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    burn_request_cancel();
    BURN_LOGI("cancel requested");
}

extern "C" JNIEXPORT void JNICALL
Java_com_burnsubtitle_ffmpeg_FFmpegEngine_nativeResetCancel(
        JNIEnv * /* env */,
        jobject /* thiz */) {
    g_cancel.store(false);
}
