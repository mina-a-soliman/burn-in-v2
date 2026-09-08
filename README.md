# Burn Subtitle

Offline Android app that **hard-burns** subtitles into video. SRT, ASS, and VTT files are drawn onto the picture with FFmpeg packaged inside the APK. There is no internet permission, no root, and users never install FFmpeg, Termux, or a separate encoder.

The app is built for Arabic as a first-class case: logical-order text, OpenType shaping, and mixed RTL/LTR lines. English and other Unicode scripts use bundled Noto Sans.

| | |
|---|---|
| Package | `com.burnsubtitle` |
| Android | 8.0+ (API 26) |
| UI | English and Arabic |
| Output | H.264 MP4, audio copied, no subtitle track left behind |

Release APKs are produced by **GitHub Actions**. You do not need Android Studio on your machine to ship a build.

## Features

- Pick a video and a subtitle file with the system document picker (Storage Access Framework). No broad storage permission.
- Burn SRT, ASS, or VTT into the video so the text is part of the picture on any player.
- Subtitle style: position (nine-point grid), size, text color, background, outline, shadow, and edge margin.
- Live preview of mixed English/Arabic sample text before export.
- Foreground export with progress, cancel, then open or share the result.
- Offline only: FFmpeg, libass, HarfBuzz, FriBidi, x264, and Noto fonts ship in the APK.
- Large videos stay as a content URI until export. Subtitle files are cached after parse (max 8 MB).

## Supported subtitle formats

| Format | Extensions | Notes |
|---|---|---|
| SubRip | `.srt` | Common downloads; UTF-8 preferred |
| WebVTT | `.vtt` | Cue text is converted to ASS for burn |
| Advanced SubStation Alpha | `.ass` / `.ssa` | Restyled to the in-app look; timing and dialogue text are kept |

Encoding: UTF-8 (with or without BOM), UTF-16 LE/BE. The burn path always writes a UTF-8 ASS file with BOM and `charenc=UTF-8`.

## Supported languages

- **App UI:** English and Arabic (follows the system language).
- **Subtitle text:** Arabic, English, and other Unicode scripts that Noto can draw.
- **Fonts in the APK:** [Noto Naskh Arabic](https://github.com/notofonts/arabic) and [Noto Sans](https://github.com/notofonts/latin-greek-cyrillic) (SIL Open Font License).

The encoder does not require a matching UI language. An English UI can still burn Arabic subtitles, and the reverse.

## Arabic RTL support

Arabic is not “flipped at the last minute.” The pipeline keeps **logical order** (the order you type and store in SRT/VTT/ASS) and lets the font shaper do joining.

1. Cues are parsed and markup that would break shaping is stripped.
2. Runs of Arabic vs Latin are classified. Arabic runs are wrapped in Unicode RLI…PDI; Latin inside mixed lines uses LRI…PDI so numbers and English stay left-to-right.
3. ASS `{\fn…}` switches to **Noto Naskh Arabic** or **Noto Sans** per run.
4. Letters are **not** converted to presentation forms (isolated/initial/medial/final). Noto needs OpenType GSUB; pre-shaping would look wrong.
5. Bundled **libass + HarfBuzz + FriBidi** shape and bidirectionally reorder at render time, then FFmpeg composites that onto each frame.

If Arabic looks disconnected, reversed, or like boxes, the APK is usually a local stub without FFmpeg/fonts — use a GitHub Actions artifact.

## How FFmpeg works

FFmpeg is compiled for Android (`arm64-v8a`, `armeabi-v7a`) in CI and linked into the app as native libraries. The user never runs a command line.

Typical encode:

1. The selected video is copied into app cache for the job.
2. Subtitles are converted to styled UTF-8 ASS.
3. Bundled fonts are unpacked to app storage and passed as `fontsdir`.
4. Native code runs an FFmpeg pipeline equivalent to:
   - video re-encode: **libx264**, **CRF 18**, `veryfast`, `yuv420p`
   - audio: **copy**
   - filter: `subtitles=…:charenc=UTF-8:fontsdir=…`
   - **`-sn`** so the output has no subtitle stream
   - `+faststart` for playback
5. On Android 10+, the MP4 is saved to **Movies/BurnSubtitle**. On Android 8–9 it is shared via FileProvider from app storage.

A **debug build from Android Studio without CI native artifacts** compiles a JNI stub. Burn then fails with “FFmpeg is not packaged in this APK.” That is expected. Install an APK from Actions.

## Build an APK with GitHub Actions

You only need git and a GitHub repository.

1. Push this project to GitHub (`main` or `master`).
2. Open the repo → **Actions** → **Build APK**.
3. The workflow also runs on every push to `main`/`master`. To run it by hand: **Run workflow**.
   - Leave **build_release** enabled to also assemble a release APK (default on).
4. Wait for the run to finish. The **first** run compiles FFmpeg and can take up to about three hours. Later runs reuse cache and are much shorter.

Workflow file: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)

Optional repository secrets for a real release signature (otherwise the release APK is debug-signed):

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

No local Android Studio, NDK, or FFmpeg install is required to produce the APK users should install.

## Download the APK artifact

1. Open **Actions** and select the finished **Build APK** run.
2. Scroll to **Artifacts**.
3. Download:
   - **`burn-subtitle-debug`** — always produced; install this to test.
   - **`burn-subtitle-release`** — on `main`/`master` pushes and on manual runs with release enabled.
4. Unzip the artifact and copy the `.apk` to the phone (USB, Drive, etc.).
5. On the device, allow install from that source if prompted, then open the APK.

Artifacts are kept for 30 days on GitHub.

## How to use the app

1. Open **Burn Subtitle**.
2. **Select video** — choose an MP4 or other file the system picker can read.
3. **Select subtitle file** — SRT, VTT, or ASS.
4. **Subtitle settings** (optional) — position, size, colors, background, outline, shadow, margin. Check the preview.
5. **Export** — keep the app open. A notification shows progress. You can cancel.
6. When it finishes: **Open** in a player or **Share**. On Android 10+, look under Movies → BurnSubtitle (`*_burned.mp4`).

Notification permission (Android 13+) is only for export progress.

## Troubleshooting

**“FFmpeg is not packaged in this APK.”**  
You installed a local stub build. Download `burn-subtitle-debug` or `burn-subtitle-release` from GitHub Actions.

**Arabic letters are disconnected, reversed, or empty boxes.**  
Use a CI-built APK so HarfBuzz/libass and Noto fonts are inside the package. Confirm the subtitle file is UTF-8 (not a broken ANSI export).

**“Choose a video file” / “Unable to read that video.”**  
Pick a real video through the system picker. Some cloud placeholders are not readable until downloaded.

**“Use an SRT, ASS, or VTT subtitle file.”**  
Wrong extension or contents. Rename is not enough if the file is not one of those formats.

**“Subtitle file is too large.”**  
Limit is 8 MB. Split or simplify the file.

**“Not enough free space to copy this video.”**  
Export copies the video into cache. Free roughly the size of the video (more is safer) and retry.

**Export sits at 0% or the phone sleeps.**  
Keep the app in the foreground. Allow notifications so the foreground worker can run.

**Share/open does nothing.**  
Install a video player. On Android 8–9 the file lives in app storage and is shared with a content URI.

**GitHub Action failed on the first run.**  
The native FFmpeg job can take hours and needs a successful cache. Re-run **Build APK**. If `native/scripts` or `third_party/ffmpeg.lock` changed, expect a full native rebuild.

**Release APK won’t install over debug.**  
Different signatures. Uninstall the debug build first, or keep using the debug artifact.

## License

The app is licensed under the [GNU General Public License v3.0](LICENSE). FFmpeg is built with GPL components (including x264). Bundled Noto fonts are under the SIL Open Font License (see `app/src/main/assets/fonts/OFL.txt`).
