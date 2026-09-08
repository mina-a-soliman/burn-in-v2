Pinned native versions for the GitHub Actions FFmpeg build.

The APK never requires a user-installed FFmpeg binary. GitHub Actions
compiles the libraries from the commits in ffmpeg.lock and packages
them into jniLibs.

Do not download dependencies onto developer machines for release builds.
