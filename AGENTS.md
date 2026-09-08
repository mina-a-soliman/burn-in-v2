# Burn Subtitle — agent memory

Offline Android app that hard-burns subtitles into video using FFmpeg packaged in the APK.

Read `.cursor/rules/project-memory.mdc` for architecture, what is already built, and how to continue.

## Constraints

- Kotlin, minSdk 26, Compose, Hilt, WorkManager
- No internet permission, no root
- Users never install FFmpeg
- Release/FFmpeg native builds: GitHub Actions only

## Current gap

CI `build-apk.yml` now restores FFmpeg `jniLibs` + headers from `native-cache.yml` before assemble. Local CMake still stubs (exit 64) until those artifacts exist on the machine.

## Verified toolchain

`:app:testDebugUnitTest`, `:app:assembleDebug` and `:app:assembleRelease` (R8) all pass with JDK 17, Gradle 9.5, AGP 9.3.1, `platforms;android-37.0`, `build-tools;36.0.0`, NDK 28.2.13676358. `native/jni/ffmpeg_burn.cpp` is only compiled when FFmpeg headers are present, so verify it with a syntax check against the pinned FFmpeg headers (`clang++ -fsyntax-only -DBURN_HAVE_FFMPEG=1`) for both ABIs after editing it.

## Known limitations (not bugs)

- Audio is stream-copied into MP4 (AAC passes through `aac_adtstoasc`); an input whose audio codec MP4 cannot hold (e.g. Vorbis, Opus in older muxers) fails the mux instead of re-encoding.
- `BurnSession` is in-memory, so a job is no longer tracked in the UI if the process is killed mid-burn (the worker itself keeps running).
- `third_party/ffmpeg.lock` pins `x264_ref=stable`, a moving branch, so the native cache key does not change when upstream x264 moves.
