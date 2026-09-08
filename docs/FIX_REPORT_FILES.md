# Corrected files - Burn Subtitle fix report

Complete contents of every source file changed during the review and fix pass. 28 files.

Generated from the working tree; do not hand-edit.

## `.github/workflows/native-cache.yml`

```yaml
name: Native FFmpeg cache

on:
  workflow_dispatch:
  workflow_call:
  push:
    paths:
      - third_party/ffmpeg.lock
      - native/scripts/**
      - native/patches/**

concurrency:
  group: native-ffmpeg-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: false

permissions:
  contents: read
  actions: write

jobs:
  ffmpeg:
    runs-on: ubuntu-latest
    timeout-minutes: 180
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Android
        uses: ./.github/actions/setup-android

      - name: Make native scripts executable
        run: chmod +x native/scripts/*.sh

      # Only the install prefix is cached. It is what both the skip check below and
      # package-jniLibs.sh read, and it keeps the entry small enough to upload; caching
      # all of native/out would include the git checkouts and object trees.
      - name: Cache native prefix
        uses: actions/cache@v4
        with:
          path: native/out/prefix
          key: ffmpeg-${{ hashFiles('third_party/ffmpeg.lock', 'native/scripts/**') }}-ndk-28.2.13676358

      - name: Detect built FFmpeg
        id: ffmpeg_built
        run: |
          if [[ -f native/out/prefix/arm64-v8a/lib/libavformat.so && -f native/out/prefix/armeabi-v7a/lib/libavformat.so ]]; then
            echo "ready=true" >> "${GITHUB_OUTPUT}"
          else
            echo "ready=false" >> "${GITHUB_OUTPUT}"
          fi

      - name: Install native build packages
        if: steps.ffmpeg_built.outputs.ready != 'true'
        run: |
          sudo apt-get update
          sudo apt-get install -y autoconf automake libtool pkg-config meson ninja-build nasm yasm git

      - name: Build FFmpeg
        if: steps.ffmpeg_built.outputs.ready != 'true'
        run: ./native/scripts/build-ffmpeg.sh

      - name: Package jniLibs and headers
        run: ./native/scripts/package-jniLibs.sh

      - name: Upload jniLibs
        uses: actions/upload-artifact@v4
        with:
          name: ffmpeg-jniLibs
          path: app/src/main/jniLibs
          if-no-files-found: error
          retention-days: 14

      - name: Upload FFmpeg headers
        uses: actions/upload-artifact@v4
        with:
          name: ffmpeg-headers
          path: native/prebuilt
          if-no-files-found: error
          retention-days: 14
```

## `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.burnsubtitle"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.burnsubtitle"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    signingConfigs {
        val storePath = System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }
            ?: (project.findProperty("SIGNING_STORE_FILE") as String?)?.takeIf { it.isNotBlank() }
        if (!storePath.isNullOrBlank()) {
            create("ciRelease") {
                storeFile = file(storePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                    ?: project.findProperty("SIGNING_STORE_PASSWORD") as String?
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                    ?: project.findProperty("SIGNING_KEY_ALIAS") as String?
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                    ?: project.findProperty("SIGNING_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("ciRelease")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("${rootDir}/native/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Debug symbols are not kept: the bundled FFmpeg/libc++ shared objects are large,
        // and stripping them keeps the APK to a size that installs on real devices.
    }

    androidResources {
        localeFilters += listOf("en", "ar")
        noCompress += listOf("ttf")
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
```

## `app/src/main/java/com/burnsubtitle/data/saf/SafFileCopier.kt`

```kotlin
package com.burnsubtitle.data.saf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.burnsubtitle.domain.error.FileSelectionException
import com.burnsubtitle.domain.subtitle.SubtitleDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafFileCopier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun copyToFile(uri: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        openRead(uri).use { input ->
            destination.outputStream().buffered(TempFileStore.COPY_BUFFER_BYTES).use { output ->
                input.copyTo(output, TempFileStore.COPY_BUFFER_BYTES)
            }
        }
        if (!destination.exists() || destination.length() <= 0L) {
            throw IOException("Copied file is empty: ${destination.absolutePath}")
        }
    }

    /** Applies the same BOM/legacy-charset detection as subtitle selection. */
    fun readText(uri: Uri): String {
        return SubtitleDecoder.decode(readBytes(uri, TempFileStore.MAX_SUBTITLE_BYTES))
    }

    fun readBytes(uri: Uri, maxBytes: Long): ByteArray {
        openRead(uri).use { input ->
            val buffer = ByteArray(DEFAULT_READ_BUFFER)
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw FileSelectionException.SubtitleTooLarge()
                }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    fun openRead(uri: Uri): InputStream {
        val fromDescriptor = runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Unable to open file descriptor for $uri")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        }
        if (fromDescriptor.isSuccess) {
            return fromDescriptor.getOrThrow()
        }
        return context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open $uri", fromDescriptor.exceptionOrNull())
    }

    companion object {
        private const val DEFAULT_READ_BUFFER = 16 * 1024
    }
}
```

## `app/src/main/java/com/burnsubtitle/data/work/BurnWorker.kt`

```kotlin
package com.burnsubtitle.data.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.burnsubtitle.R
import com.burnsubtitle.data.media.MediaStoreExporter
import com.burnsubtitle.data.saf.TempFileStore
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.ffmpeg.FFmpegEngine
import com.burnsubtitle.ffmpeg.FFmpegException
import com.burnsubtitle.ffmpeg.SubtitleBurnProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@HiltWorker
class BurnWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: FFmpegEngine,
    private val processor: SubtitleBurnProcessor,
    private val exporter: MediaStoreExporter,
    private val notifier: BurnForegroundNotifier,
    private val tempFiles: TempFileStore,
) : CoroutineWorker(context, params) {

    private val stopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun doWork(): Result {
        engine.resetForNewJob()
        val job = jobFromInput() ?: return Result.failure(workDataOf(KEY_ERROR to "Missing burn job"))
        notifyForeground(0)
        return coroutineScope {
            val progressJob = launch {
                engine.progress
                    .map { progress -> (progress.fraction * 100).toInt().coerceIn(0, 100) }
                    .distinctUntilChanged()
                    .collect { percent ->
                        notifyForeground(percent)
                        setProgress(workDataOf(KEY_PROGRESS to percent))
                    }
            }
            // CoroutineWorker.onStopped is final, and the native burn blocks until it observes
            // the cancel flag, so watch isStopped from a scope the stop does not cancel.
            val stopWatcher = stopScope.launch {
                while (true) {
                    if (isStopped) {
                        processor.cancel()
                        break
                    }
                    delay(STOP_POLL_MS)
                }
            }
            try {
                processor.run(job)
                progressJob.cancel()
                if (isStopped) {
                    return@coroutineScope cancelledResult()
                }
                val output = File(job.outputPath)
                if (!output.exists() || output.length() == 0L) {
                    return@coroutineScope Result.failure(workDataOf(KEY_ERROR to errorMessage(FFmpegException.InvalidOutput())))
                }
                val uri = withContext(Dispatchers.IO) {
                    exporter.exportVideo(output, job.displayName)
                }
                // The burn is published to MediaStore (or the shared dir pre-Q), so the
                // input copy and the encoder output must not keep ~2x the video in cache.
                tempFiles.deleteJobDir(job.id)
                Result.success(
                    workDataOf(
                        KEY_OUTPUT_URI to uri.toString(),
                        KEY_OUTPUT_PATH to output.absolutePath,
                        KEY_DISPLAY_NAME to job.displayName,
                    ),
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                processor.cancel()
                tempFiles.deleteJobDir(job.id)
                throw cancelled
            } catch (error: FFmpegException.Cancelled) {
                tempFiles.deleteJobDir(job.id)
                cancelledResult()
            } catch (error: FFmpegException) {
                Result.failure(
                    workDataOf(
                        KEY_ERROR to errorMessage(error),
                        KEY_EXIT to ((error as? FFmpegException.Failed)?.exitCode ?: Int.MIN_VALUE),
                    ),
                )
            } catch (error: Exception) {
                Result.failure(workDataOf(KEY_ERROR to (error.message ?: error.javaClass.simpleName)))
            } finally {
                progressJob.cancel()
                stopWatcher.cancel()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0)

    /**
     * A stopped worker can no longer own a foreground service, and the OS rejects the
     * notification once POST_NOTIFICATIONS is denied. Neither case should fail the burn.
     */
    private suspend fun notifyForeground(percent: Int) {
        if (isStopped) return
        try {
            setForeground(foregroundInfo(percent))
        } catch (_: IllegalStateException) {
            // Worker was stopped between the check and the call.
        }
    }

    private fun cancelledResult(): Result {
        return Result.failure(
            workDataOf(
                KEY_CANCELLED to true,
                KEY_ERROR to applicationContext.getString(R.string.error_ffmpeg_cancelled),
            ),
        )
    }

    private fun errorMessage(error: FFmpegException): String {
        val res = when (error) {
            is FFmpegException.NotAvailable -> R.string.error_ffmpeg_missing
            is FFmpegException.Cancelled -> R.string.error_ffmpeg_cancelled
            is FFmpegException.InvalidOutput -> R.string.error_ffmpeg_no_output
            is FFmpegException.Failed -> R.string.error_ffmpeg_failed
        }
        return applicationContext.getString(res)
    }

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        val type = when {
            Build.VERSION.SDK_INT >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        return ForegroundInfo(
            BurnForegroundNotifier.NOTIFICATION_ID,
            notifier.build(percent),
            type,
        )
    }

    private fun jobFromInput(): BurnJob? {
        val id = inputData.getString(KEY_ID) ?: return null
        val video = inputData.getString(KEY_VIDEO) ?: return null
        val ass = inputData.getString(KEY_ASS) ?: return null
        val output = inputData.getString(KEY_OUTPUT) ?: return null
        val fonts = inputData.getString(KEY_FONTS) ?: return null
        return BurnJob(
            id = id,
            videoCachePath = video,
            assPath = ass,
            outputPath = output,
            fontsDir = fonts,
            style = SubtitleStyle(
                position = SubtitlePosition.entries[
                    inputData.getInt(KEY_POSITION, SubtitlePosition.BOTTOM_CENTER.ordinal)
                        .coerceIn(0, SubtitlePosition.entries.lastIndex),
                ],
                fontSize = inputData.getInt(KEY_FONT_SIZE, 42),
                textColorArgb = inputData.getLong(KEY_TEXT_COLOR, 0xFFFFFFFF),
                backgroundEnabled = inputData.getBoolean(KEY_BG_ENABLED, true),
                backgroundColorArgb = inputData.getLong(KEY_BG_COLOR, 0x99000000),
                outlineEnabled = inputData.getBoolean(KEY_OUTLINE_ENABLED, true),
                outlineWidth = inputData.getFloat(KEY_OUTLINE_WIDTH, 2.5f),
                outlineColorArgb = inputData.getLong(KEY_OUTLINE_COLOR, 0xFF000000),
                shadowEnabled = inputData.getBoolean(KEY_SHADOW_ENABLED, true),
                shadowDepth = inputData.getFloat(KEY_SHADOW_DEPTH, 2.0f),
                shadowColorArgb = inputData.getLong(KEY_SHADOW_COLOR, 0x80000000),
                fontFamily = inputData.getString(KEY_FONT) ?: SubtitleStyle.DEFAULT_FONT_FAMILY,
                marginPercent = inputData.getInt(KEY_MARGIN, 6),
            ),
            videoWidth = inputData.getInt(KEY_WIDTH, 1280),
            videoHeight = inputData.getInt(KEY_HEIGHT, 720),
            durationMs = inputData.getLong(KEY_DURATION, 0L),
            displayName = inputData.getString(KEY_DISPLAY_NAME) ?: "burned.mp4",
        )
    }

    companion object {
        private const val STOP_POLL_MS = 250L
        const val TAG = "burn-subtitle"
        const val KEY_ID = "id"
        const val KEY_VIDEO = "video"
        const val KEY_ASS = "ass"
        const val KEY_OUTPUT = "output"
        const val KEY_FONTS = "fonts"
        const val KEY_POSITION = "position"
        const val KEY_FONT_SIZE = "fontSize"
        const val KEY_TEXT_COLOR = "textColor"
        const val KEY_BG_ENABLED = "bgEnabled"
        const val KEY_BG_COLOR = "bgColor"
        const val KEY_OUTLINE_ENABLED = "outlineEnabled"
        const val KEY_OUTLINE_WIDTH = "outlineWidth"
        const val KEY_OUTLINE_COLOR = "outlineColor"
        const val KEY_SHADOW_ENABLED = "shadowEnabled"
        const val KEY_SHADOW_DEPTH = "shadowDepth"
        const val KEY_SHADOW_COLOR = "shadowColor"
        const val KEY_FONT = "font"
        const val KEY_MARGIN = "margin"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_DURATION = "duration"
        const val KEY_DISPLAY_NAME = "displayName"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_URI = "outputUri"
        const val KEY_OUTPUT_PATH = "outputPath"
        const val KEY_ERROR = "error"
        const val KEY_EXIT = "exit"
        const val KEY_CANCELLED = "cancelled"

        fun inputData(job: BurnJob) = workDataOf(
            KEY_ID to job.id,
            KEY_VIDEO to job.videoCachePath,
            KEY_ASS to job.assPath,
            KEY_OUTPUT to job.outputPath,
            KEY_FONTS to job.fontsDir,
            KEY_POSITION to job.style.position.ordinal,
            KEY_FONT_SIZE to job.style.fontSize,
            KEY_TEXT_COLOR to job.style.textColorArgb,
            KEY_BG_ENABLED to job.style.backgroundEnabled,
            KEY_BG_COLOR to job.style.backgroundColorArgb,
            KEY_OUTLINE_ENABLED to job.style.outlineEnabled,
            KEY_OUTLINE_WIDTH to job.style.outlineWidth,
            KEY_OUTLINE_COLOR to job.style.outlineColorArgb,
            KEY_SHADOW_ENABLED to job.style.shadowEnabled,
            KEY_SHADOW_DEPTH to job.style.shadowDepth,
            KEY_SHADOW_COLOR to job.style.shadowColorArgb,
            KEY_FONT to job.style.fontFamily,
            KEY_MARGIN to job.style.marginPercent,
            KEY_WIDTH to job.videoWidth,
            KEY_HEIGHT to job.videoHeight,
            KEY_DURATION to job.durationMs,
            KEY_DISPLAY_NAME to job.displayName,
        )

        fun parseId(raw: String): UUID = UUID.fromString(raw)
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/ass/AssAlignment.kt`

```kotlin
package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle

data class AssMargins(
    val left: Int,
    val right: Int,
    val vertical: Int,
)

object AssAlignment {
    fun fromPosition(position: SubtitlePosition): Int = position.assAlignment

    fun margins(position: SubtitlePosition, playResX: Int, playResY: Int, marginPercent: Int): AssMargins {
        val percent = marginPercent.coerceIn(
            SubtitleStyle.MIN_MARGIN_PERCENT,
            SubtitleStyle.MAX_MARGIN_PERCENT,
        )
        val vertical = ((playResY.coerceAtLeast(1) * percent) / 100)
        val horizontal = ((playResX.coerceAtLeast(1) * percent) / 100)
        val left = when (position) {
            SubtitlePosition.BOTTOM_LEFT, SubtitlePosition.MIDDLE_LEFT, SubtitlePosition.TOP_LEFT -> horizontal
            else -> horizontal
        }
        val right = when (position) {
            SubtitlePosition.BOTTOM_RIGHT, SubtitlePosition.MIDDLE_RIGHT, SubtitlePosition.TOP_RIGHT -> horizontal
            else -> horizontal
        }
        return AssMargins(left = left, right = right, vertical = vertical)
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/ass/AssDocumentWriter.kt`

```kotlin
package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssDocumentWriter @Inject constructor(
    private val styleGenerator: AssStyleGenerator,
) {
    fun write(
        document: SubtitleDocument,
        style: SubtitleStyle,
        playResX: Int,
        playResY: Int,
    ): String {
        val width = playResX.coerceAtLeast(1)
        val height = playResY.coerceAtLeast(1)
        val styleLine = styleGenerator.toStyleLine(style, width, height)
        // libass matches section headers and field names at column 0, so every line is
        // emitted unindented. A raw string with trimIndent() cannot be used here: the
        // interpolated dialogue block is multi-line, which makes the common indent 0 and
        // leaves the surrounding literal indented.
        val lines = buildList {
            add("[Script Info]")
            add("Title: Burn Subtitle")
            add("ScriptType: v4.00+")
            add("WrapStyle: 0")
            add("ScaledBorderAndShadow: yes")
            add("Kerning: yes")
            add("YCbCr Matrix: TV.709")
            add("PlayResX: $width")
            add("PlayResY: $height")
            add("LayoutResX: $width")
            add("LayoutResY: $height")
            add("")
            add("[V4+ Styles]")
            add(STYLE_FORMAT)
            add(styleLine)
            add("")
            add("[Events]")
            add(EVENT_FORMAT)
            document.cues.forEach { cue -> add(dialogueLine(cue)) }
        }
        return "\uFEFF" + lines.joinToString("\n") + "\n"
    }

    private fun dialogueLine(cue: SubtitleCue): String {
        return "Dialogue: 0," +
            AssTextEncoder.formatTime(cue.startMs) + "," +
            AssTextEncoder.formatTime(cue.endMs) +
            ",Default,,0,0,0,," +
            AssTextEncoder.encode(cue.text)
    }

    private companion object {
        const val STYLE_FORMAT = "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, " +
            "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, " +
            "Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding"
        const val EVENT_FORMAT =
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/ass/AssStyleGenerator.kt`

```kotlin
package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.subtitle.SubtitleFonts
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssStyleGenerator @Inject constructor() {
    fun toStyleLine(style: SubtitleStyle, playResX: Int, playResY: Int): String {
        val fontSize = AssMetrics.fontSize(style.fontSize, playResY)
        val outline = AssMetrics.outline(style.outlineWidth, playResY, style.outlineEnabled)
        val shadow = AssMetrics.shadow(style.shadowDepth, playResY, style.shadowEnabled)
        val borderStyle = if (style.backgroundEnabled) 3 else 1
        val margins = AssAlignment.margins(style.position, playResX, playResY, style.marginPercent)
        val backColour = if (style.backgroundEnabled) {
            AssColor.fromArgb(style.backgroundColorArgb)
        } else {
            AssColor.fromArgb(style.shadowColorArgb)
        }
        // Style lines are comma separated, so a comma in the family name would shift
        // every field after it.
        val fontName = style.fontFamily
            .replace(',', ' ')
            .trim()
            .ifBlank { SubtitleFonts.ARABIC_FAMILY }
        return buildString {
            append("Style: Default,")
            append(fontName)
            append(',')
            append(fontSize)
            append(',')
            append(AssColor.fromArgb(style.textColorArgb))
            append(',')
            append(AssColor.fromArgb(style.textColorArgb))
            append(',')
            append(AssColor.fromArgb(style.outlineColorArgb))
            append(',')
            append(backColour)
            append(",0,0,0,0,100,100,0,0,")
            append(borderStyle)
            append(',')
            append("%.2f".format(Locale.US, outline))
            append(',')
            append("%.2f".format(Locale.US, shadow))
            append(',')
            append(AssAlignment.fromPosition(style.position))
            append(',')
            append(margins.left)
            append(',')
            append(margins.right)
            append(',')
            append(margins.vertical)
            append(",1")
        }
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/parser/VttParser.kt`

```kotlin
package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat

class VttParser : SubtitleParser {
    override val format: SubtitleFormat = SubtitleFormat.VTT

    override fun parse(text: String): SubtitleDocument {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val body = normalized.substringAfter("WEBVTT", missingDelimiterValue = normalized)
        val blocks = body.split(BLOCK_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        val cues = blocks.mapIndexedNotNull { index, block -> parseBlock(block, index) }
        require(cues.isNotEmpty()) { "VTT file contains no cues" }
        return SubtitleDocument(cues = cues, sourceFormat = format)
    }

    private fun parseBlock(block: String, fallbackIndex: Int): SubtitleCue? {
        val lines = block.lines().filter { !it.startsWith("NOTE") && !it.startsWith("STYLE") }
        val timeLineIndex = lines.indexOfFirst { ARROW in it }
        if (timeLineIndex < 0) return null
        val times = lines[timeLineIndex].split(ARROW, limit = 2)
        if (times.size != 2) return null
        val start = parseTimestamp(times[0].trim()) ?: return null
        // "--> 00:00:02.500 align:start" splits with a leading space, so trim before
        // dropping the cue settings that follow the end timestamp.
        val end = parseTimestamp(times[1].trim().substringBefore(' ')) ?: return null
        val text = lines.drop(timeLineIndex + 1)
            .joinToString("\n")
            .replace(TAG, "")
            .trim()
        if (text.isEmpty()) return null
        return SubtitleCue(index = fallbackIndex + 1, startMs = start, endMs = end, text = text)
    }

    companion object {
        private const val ARROW = "-->"
        private val BLOCK_SPLIT = Regex("\n\n+")
        private val TAG = Regex("<[^>]+>")
        private val TIMESTAMP = Regex("""(?:(\d{1,3}):)?(\d{1,2}):(\d{2})\.(\d{1,3})""")

        fun parseTimestamp(value: String): Long? {
            val match = TIMESTAMP.find(value) ?: return SrtParser.parseTimestamp(value.replace('.', ','))
            val hours = match.groupValues[1].ifEmpty { "0" }.toLong()
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            val fraction = match.groupValues[4].padEnd(3, '0').take(3).toLong()
            return (((hours * 60) + minutes) * 60 + seconds) * 1000 + fraction
        }
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/subtitle/SubtitleDecoder.kt`

```kotlin
package com.burnsubtitle.domain.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes subtitle bytes to text.
 *
 * Byte order marks win when present. Otherwise UTF-8 is attempted strictly, because a
 * strict decode only succeeds on genuine UTF-8. Files that fail are legacy single-byte
 * encodings; Arabic subtitles in the wild are overwhelmingly windows-1256, and Latin
 * ones windows-1252, so the fallback picks whichever produces more Arabic letters.
 */
object SubtitleDecoder {

    fun decode(bytes: ByteArray): String {
        bomCharset(bytes)?.let { (charset, offset) ->
            return String(bytes, offset, bytes.size - offset, charset)
        }
        // BOM-less UTF-16 is valid UTF-8 (NUL is a legal code point), so a strict UTF-8
        // decode would "succeed" and yield text riddled with NULs. Detect it first.
        bomlessUtf16Charset(bytes)?.let { charset -> return String(bytes, charset) }
        strictDecode(bytes, Charsets.UTF_8)?.let { return it }
        val arabic = legacyCharset(WINDOWS_1256)?.let { strictDecode(bytes, it) }
        val latin = legacyCharset(WINDOWS_1252)?.let { strictDecode(bytes, it) }
        return when {
            // Arabic words are contiguous letter runs. A lone Arabic letter is far more
            // likely to be an accented Latin character that windows-1256 happens to map.
            arabic != null && longestArabicRun(arabic) >= MIN_ARABIC_RUN -> arabic
            latin != null -> latin
            arabic != null -> arabic
            else -> String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun bomCharset(bytes: ByteArray): Pair<Charset, Int>? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8 to 3
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE to 2
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE to 2
        }
        return null
    }

    /**
     * Subtitle text is overwhelmingly ASCII-range (digits, arrows, timing punctuation), so
     * UTF-16 shows up as every other byte being NUL. Which half holds the NULs gives the
     * endianness.
     */
    private fun bomlessUtf16Charset(bytes: ByteArray): Charset? {
        val sampled = minOf(bytes.size, UTF16_SAMPLE_BYTES) and 1.inv()
        if (sampled < UTF16_MIN_BYTES) return null
        var evenNuls = 0
        var oddNuls = 0
        for (index in 0 until sampled) {
            if (bytes[index] == 0.toByte()) {
                if (index and 1 == 0) evenNuls++ else oddNuls++
            }
        }
        val pairs = sampled / 2
        val threshold = (pairs * UTF16_NUL_RATIO).toInt()
        return when {
            oddNuls >= threshold && evenNuls == 0 -> Charsets.UTF_16LE
            evenNuls >= threshold && oddNuls == 0 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun legacyCharset(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private fun longestArabicRun(text: String): Int {
        var longest = 0
        var current = 0
        text.forEach { char ->
            if (char in '\u0620'..'\u064A' || char in '\u0671'..'\u06D3') {
                current++
                if (current > longest) longest = current
            } else {
                current = 0
            }
        }
        return longest
    }

    private const val MIN_ARABIC_RUN = 2
    private const val UTF16_SAMPLE_BYTES = 4096
    private const val UTF16_MIN_BYTES = 16
    private const val UTF16_NUL_RATIO = 0.6
    private const val WINDOWS_1256 = "windows-1256"
    private const val WINDOWS_1252 = "windows-1252"
}
```

## `app/src/main/java/com/burnsubtitle/domain/subtitle/SubtitleEngine.kt`

```kotlin
package com.burnsubtitle.domain.subtitle

import com.burnsubtitle.domain.ass.AssDocumentWriter
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.parser.SubtitleFormatDetector
import com.burnsubtitle.domain.parser.SubtitleParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleEngine @Inject constructor(
    // Without @JvmSuppressWildcards the injection site asks Dagger for
    // List<? extends SubtitleParser>, which no module provides.
    private val parsers: List<@JvmSuppressWildcards SubtitleParser>,
    private val writer: AssDocumentWriter,
) {
    fun parse(text: String, format: SubtitleFormat): SubtitleDocument {
        val parser = parsers.firstOrNull { it.format == format }
            ?: throw IllegalArgumentException("Unsupported subtitle format $format")
        val document = parser.parse(SubtitleText.normalize(text))
        val cues = document.cues.map { cue ->
            cue.copy(text = SubtitleMarkup.strip(cue.text))
        }.filter { it.text.isNotBlank() }
        require(cues.isNotEmpty()) { "Subtitle file contains no readable cues" }
        return document.copy(cues = cues)
    }

    fun parse(text: String, fileName: String, mimeType: String? = null): SubtitleDocument {
        val normalized = SubtitleText.normalize(text)
        val format = SubtitleFormatDetector.detect(fileName, mimeType, normalized)
            ?: throw IllegalArgumentException("Unsupported subtitle format")
        return parse(normalized, format)
    }

    fun toAss(
        document: SubtitleDocument,
        style: SubtitleStyle,
        playResX: Int,
        playResY: Int,
    ): String {
        return writer.write(document, style, playResX, playResY)
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/usecase/PrepareBurnJobUseCase.kt`

```kotlin
package com.burnsubtitle.domain.usecase

import android.content.Context
import android.net.Uri
import com.burnsubtitle.data.saf.SafFileCopier
import com.burnsubtitle.data.saf.TempFileStore
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.SubtitleSource
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.model.VideoSource
import com.burnsubtitle.domain.subtitle.SubtitleDecoder
import com.burnsubtitle.domain.subtitle.SubtitleEngine
import com.burnsubtitle.domain.subtitle.SubtitleText
import com.burnsubtitle.domain.util.MediaFileNames
import com.burnsubtitle.ffmpeg.FontRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class PrepareBurnJobUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val copier: SafFileCopier,
    private val tempFiles: TempFileStore,
    private val engine: SubtitleEngine,
    private val fontRuntime: FontRuntime,
) {
    suspend operator fun invoke(
        video: VideoSource,
        subtitle: SubtitleSource,
        style: SubtitleStyle,
    ): BurnJob = withContext(Dispatchers.IO) {
        tempFiles.requireSpaceForVideo(video.sizeBytes)
        val jobId = UUID.randomUUID().toString()
        val workDir = tempFiles.createJobDir(jobId)
        try {
            val videoExtension = MediaFileNames.extension(video.displayName).ifBlank { "mp4" }
            val videoFile = File(workDir, "input.$videoExtension")
            copier.copyToFile(Uri.parse(video.contentUri), videoFile)
            val subtitleText = readSubtitle(subtitle)
            val document = engine.parse(subtitleText, subtitle.format)
            val assFile = File(workDir, "styled.ass")
            assFile.writeText(
                engine.toAss(
                    document = document,
                    style = style,
                    playResX = video.width.coerceAtLeast(1),
                    playResY = video.height.coerceAtLeast(1),
                ),
                Charsets.UTF_8,
            )
            val output = File(workDir, "output.mp4")
            BurnJob(
                id = jobId,
                videoCachePath = videoFile.absolutePath,
                assPath = assFile.absolutePath,
                outputPath = output.absolutePath,
                fontsDir = fontRuntime.prepare(context).absolutePath,
                style = style,
                videoWidth = video.width,
                videoHeight = video.height,
                durationMs = video.durationMs,
                displayName = burnedName(video.displayName),
            )
        } catch (cancelled: CancellationException) {
            tempFiles.deleteJobDir(jobId)
            throw cancelled
        } catch (error: Throwable) {
            tempFiles.deleteJobDir(jobId)
            throw error
        }
    }

    private fun readSubtitle(subtitle: SubtitleSource): String {
        val cached = subtitle.cachePath?.let { File(it) }
        if (cached != null && cached.exists() && cached.length() > 0L) {
            // The cache is written as UTF-8, but decode defensively in case it is stale.
            return SubtitleText.normalize(SubtitleDecoder.decode(cached.readBytes()))
        }
        return SubtitleText.normalize(copier.readText(Uri.parse(subtitle.contentUri)))
    }

    private fun burnedName(original: String): String {
        val base = MediaFileNames.sanitize(original.substringBeforeLast('.', original))
        return "${base}_burned.mp4"
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/usecase/ResetSessionUseCase.kt`

```kotlin
package com.burnsubtitle.domain.usecase

import android.net.Uri
import com.burnsubtitle.data.saf.SafUriPermissions
import com.burnsubtitle.domain.session.BurnSession
import javax.inject.Inject

/**
 * Clears the session for a fresh pick.
 *
 * Picking a file takes a persistable read grant, and picking a replacement releases the
 * one before it. "Start over" has no replacement to compare against, so without this the
 * grants accumulate for the lifetime of the install until the platform cap drops them.
 */
class ResetSessionUseCase @Inject constructor(
    private val session: BurnSession,
    private val permissions: SafUriPermissions,
) {
    operator fun invoke() {
        session.video.value?.contentUri?.let(::releaseGrant)
        session.subtitle.value?.contentUri?.let(::releaseGrant)
        session.resetAll()
    }

    private fun releaseGrant(contentUri: String) {
        if (contentUri.isBlank()) return
        runCatching { Uri.parse(contentUri) }.getOrNull()?.let(permissions::release)
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/usecase/SelectSubtitleUseCase.kt`

```kotlin
package com.burnsubtitle.domain.usecase

import android.net.Uri
import com.burnsubtitle.data.saf.SafDocumentQuery
import com.burnsubtitle.data.saf.SafFileCopier
import com.burnsubtitle.data.saf.SafUriPermissions
import com.burnsubtitle.data.saf.TempFileStore
import com.burnsubtitle.domain.error.FileSelectionException
import com.burnsubtitle.domain.model.SubtitleSource
import com.burnsubtitle.domain.parser.SubtitleFormatDetector
import com.burnsubtitle.domain.subtitle.SubtitleDecoder
import com.burnsubtitle.domain.subtitle.SubtitleEngine
import com.burnsubtitle.domain.subtitle.SubtitleText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SelectSubtitleUseCase @Inject constructor(
    private val documents: SafDocumentQuery,
    private val permissions: SafUriPermissions,
    private val copier: SafFileCopier,
    private val tempFiles: TempFileStore,
    private val engine: SubtitleEngine,
) {
    suspend operator fun invoke(uri: Uri, previousUri: String? = null): SubtitleSource {
        return withContext(Dispatchers.IO) {
            val info = documents.query(uri)
            if (info.sizeKnown && info.sizeBytes == 0L) {
                throw FileSelectionException.SubtitleInvalid()
            }
            if (info.sizeKnown && info.sizeBytes > TempFileStore.MAX_SUBTITLE_BYTES) {
                throw FileSelectionException.SubtitleTooLarge()
            }
            permissions.takePersistableRead(uri)
            try {
                val bytes = runCatching {
                    copier.readBytes(uri, TempFileStore.MAX_SUBTITLE_BYTES)
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    if (error is FileSelectionException) throw error
                    throw FileSelectionException.SubtitleUnreadable(error)
                }
                if (bytes.isEmpty()) {
                    throw FileSelectionException.SubtitleInvalid()
                }
                val text = SubtitleText.normalize(SubtitleDecoder.decode(bytes))
                val format = SubtitleFormatDetector.detect(info.displayName, info.mimeType, text)
                    ?: throw FileSelectionException.UnsupportedSubtitle()
                val document = runCatching { engine.parse(text, format) }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    throw FileSelectionException.SubtitleInvalid(error)
                }
                val cacheFile = tempFiles.writeSubtitlePick(
                    text.toByteArray(Charsets.UTF_8),
                    format.primaryExtension,
                )
                permissions.releaseIfDifferent(previousUri, uri)
                SubtitleSource(
                    contentUri = uri.toString(),
                    displayName = info.displayName,
                    format = format,
                    sizeBytes = if (info.sizeBytes > 0L) info.sizeBytes else bytes.size.toLong(),
                    cueCount = document.cues.size,
                    cachePath = cacheFile.absolutePath,
                )
            } catch (cancelled: CancellationException) {
                if (previousUri != uri.toString()) {
                    permissions.release(uri)
                }
                throw cancelled
            } catch (error: Throwable) {
                if (previousUri != uri.toString()) {
                    permissions.release(uri)
                }
                throw error
            }
        }
    }

}
```

## `app/src/main/java/com/burnsubtitle/domain/usecase/StartBurnUseCase.kt`

```kotlin
package com.burnsubtitle.domain.usecase

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.burnsubtitle.data.work.BurnWorker
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.ffmpeg.SubtitleBurnProcessor
import javax.inject.Inject

class StartBurnUseCase @Inject constructor(
    private val workManager: WorkManager,
    private val processor: SubtitleBurnProcessor,
) {
    operator fun invoke(job: BurnJob): String {
        val request = OneTimeWorkRequestBuilder<BurnWorker>()
            .setId(java.util.UUID.fromString(job.id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(BurnWorker.inputData(job))
            .addTag(BurnWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return job.id
    }

    fun cancel(jobId: String) {
        processor.cancel()
        workManager.cancelWorkById(java.util.UUID.fromString(jobId))
    }

    companion object {
        // One constant name so a new export replaces a burn that is still running.
        // Keying it by job id made every export a distinct chain, which let two
        // encodes run at once and fight over the notification and the cancel flag.
        const val UNIQUE_WORK_NAME = "burn-export"
    }
}
```

## `app/src/main/java/com/burnsubtitle/domain/util/MediaFileNames.kt`

```kotlin
package com.burnsubtitle.domain.util

object MediaFileNames {
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "mov", "m4v", "avi", "3gp", "3gpp",
        "ts", "m2ts", "mpeg", "mpg", "flv", "wmv",
    )

    fun extension(name: String): String {
        return name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    }

    fun withExtension(baseName: String, extension: String): String {
        val clean = extension.trimStart('.').lowercase()
        return if (clean.isEmpty()) baseName else "$baseName.$clean"
    }

    /**
     * SAF display names are provider-controlled strings. MediaStore rejects DISPLAY_NAME
     * values containing path separators, and they would escape the export directory on
     * the pre-Q FileProvider path.
     */
    fun sanitize(name: String, fallback: String = "video"): String {
        val cleaned = name
            .map { char -> if (char in ILLEGAL_NAME_CHARS || char.isISOControl()) '_' else char }
            .joinToString("")
            .trim()
            .trim('.')
        return cleaned.take(MAX_NAME_LENGTH).ifBlank { fallback }
    }

    fun isVideoFile(displayName: String, mimeType: String?): Boolean {
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        if (mime.startsWith("video/")) return true
        if (mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("text/")) {
            return false
        }
        return extension(displayName) in VIDEO_EXTENSIONS
    }

    private const val MAX_NAME_LENGTH = 120
    private val ILLEGAL_NAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
}
```

## `app/src/main/java/com/burnsubtitle/ffmpeg/FFmpegEngine.kt`

```kotlin
package com.burnsubtitle.ffmpeg

import androidx.annotation.Keep
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.BurnProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FFmpegEngine @Inject constructor(
    private val progressParser: FFmpegProgressParser,
) {
    private val mutex = Mutex()
    private val _progress = MutableStateFlow(BurnProgress(0f, 0L, 0L))
    val progress: StateFlow<BurnProgress> = _progress.asStateFlow()

    @Volatile
    private var durationMs: Long = 0L

    @Volatile
    private var cancelRequested: Boolean = false

    private val recentLogs = ArrayDeque<String>(MAX_LOG_LINES)

    val lastLogs: String
        get() = synchronized(recentLogs) { recentLogs.joinToString("\n") }

    fun load() {
        synchronized(loadLock) {
            if (loaded) return
            DEPENDENCY_LIBS.forEach { name ->
                runCatching { System.loadLibrary(name) }
            }
            System.loadLibrary(LIBRARY)
            loaded = true
        }
    }

    fun resetForNewJob() {
        cancelRequested = false
        if (loaded) nativeResetCancel()
    }

    fun configureFonts(fontConfigPath: String?, fontsDir: String): Int {
        load()
        return nativeInit(fontConfigPath, fontsDir)
    }

    fun cancel() {
        cancelRequested = true
        if (loaded) nativeCancel()
    }

    fun isCancelRequested(): Boolean = cancelRequested

    suspend fun execute(job: BurnJob, fontConfigPath: String?): Int = mutex.withLock {
        load()
        if (cancelRequested) return FFmpegCommandFactory.EXIT_CANCELLED
        durationMs = job.durationMs
        synchronized(recentLogs) { recentLogs.clear() }
        _progress.value = BurnProgress(0f, 0L, durationMs)
        val init = configureFonts(fontConfigPath, job.fontsDir)
        if (init == FFmpegCommandFactory.EXIT_NOT_LINKED) return init
        if (cancelRequested) return FFmpegCommandFactory.EXIT_CANCELLED
        withContext(Dispatchers.IO) {
            if (cancelRequested) return@withContext FFmpegCommandFactory.EXIT_CANCELLED
            nativeBurn(
                inputPath = job.videoCachePath,
                outputPath = job.outputPath,
                assPath = job.assPath,
                fontsDir = job.fontsDir,
                fontConfigPath = fontConfigPath,
                crf = FFmpegCommandFactory.CRF,
                preset = FFmpegCommandFactory.PRESET,
                durationMs = job.durationMs,
            )
        }
    }

    @Keep
    @Suppress("unused")
    fun onLogLine(line: String) {
        rememberLog(line)
        progressParser.parseDurationMs(line)?.let { parsed ->
            if (parsed > 0L && durationMs <= 0L) durationMs = parsed
        }
        val time = progressParser.parseTimeMs(line) ?: return
        emitProgress(time, line)
    }

    @Keep
    @Suppress("unused")
    fun onNativeProgress(timeMs: Long) {
        emitProgress(timeMs, "")
    }

    private fun emitProgress(timeMs: Long, line: String) {
        val duration = durationMs
        val fraction = if (duration <= 0L) 0f else (timeMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        _progress.value = BurnProgress(fraction, timeMs, duration, line)
    }

    private fun rememberLog(line: String) {
        synchronized(recentLogs) {
            if (recentLogs.size >= MAX_LOG_LINES) recentLogs.removeFirst()
            recentLogs.addLast(line)
        }
    }

    private external fun nativeInit(fontConfigPath: String?, fontsDir: String): Int
    private external fun nativeBurn(
        inputPath: String,
        outputPath: String,
        assPath: String,
        fontsDir: String,
        fontConfigPath: String?,
        crf: Int,
        preset: String,
        durationMs: Long,
    ): Int
    private external fun nativeCancel()
    private external fun nativeResetCancel()

    companion object {
        private const val LIBRARY = "burnffmpeg"
        private const val MAX_LOG_LINES = 40
        private val loadLock = Any()
        @Volatile
        private var loaded = false
        /**
         * Only the libraries actually shipped as shared objects. libass, HarfBuzz, FreeType
         * and FriBidi are linked statically into libavfilter, so naming them here just
         * produced load failures that were swallowed on every startup.
         */
        private val DEPENDENCY_LIBS = listOf(
            "c++_shared",
            "avutil",
            "swresample",
            "swscale",
            "avcodec",
            "avformat",
            "avfilter",
        )
    }
}
```

## `app/src/main/java/com/burnsubtitle/ui/encode/EncodeViewModel.kt`

```kotlin
package com.burnsubtitle.ui.encode

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.burnsubtitle.data.work.BurnWorker
import com.burnsubtitle.domain.model.BurnResult
import com.burnsubtitle.domain.session.BurnSession
import com.burnsubtitle.domain.usecase.ResetSessionUseCase
import com.burnsubtitle.domain.usecase.StartBurnUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExportStatus {
    QUEUED,
    PREPARING,
    ENCODING,
    FINISHING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class EncodeUiState(
    val progress: Int = 0,
    val running: Boolean = true,
    val status: ExportStatus = ExportStatus.PREPARING,
    val videoName: String = "",
    val subtitleName: String = "",
    val result: BurnResult? = null,
)

@HiltViewModel
class EncodeViewModel @Inject constructor(
    private val session: BurnSession,
    private val workManager: WorkManager,
    private val startBurn: StartBurnUseCase,
    private val resetSession: ResetSessionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EncodeUiState(
            videoName = session.video.value?.displayName.orEmpty(),
            subtitleName = session.subtitle.value?.displayName.orEmpty(),
            running = session.jobId.value != null,
            status = if (session.jobId.value == null) ExportStatus.FAILED else ExportStatus.PREPARING,
            result = session.result.value,
        ),
    )
    val state: StateFlow<EncodeUiState> = _state.asStateFlow()

    init {
        val jobId = session.jobId.value
        if (jobId != null) {
            viewModelScope.launch {
                workManager.getWorkInfoByIdFlow(BurnWorker.parseId(jobId)).collect { info ->
                    if (info == null) return@collect
                    val progress = info.progress.getInt(BurnWorker.KEY_PROGRESS, 0)
                    val finished = info.state.isFinished
                    val result = if (finished) info.toBurnResult() else null
                    if (result != null) session.setResult(result)
                    _state.update {
                        it.copy(
                            progress = if (finished && info.state == WorkInfo.State.SUCCEEDED) 100 else progress,
                            running = !finished,
                            status = info.toStatus(progress),
                            result = result ?: it.result,
                        )
                    }
                }
            }
        } else if (session.result.value != null) {
            _state.update {
                it.copy(
                    running = false,
                    status = session.result.value.toStatus(),
                    result = session.result.value,
                    progress = if (session.result.value is BurnResult.Success) 100 else it.progress,
                )
            }
        }
    }

    fun cancel() {
        session.jobId.value?.let { startBurn.cancel(it) }
        // Stop showing progress right away; the work observer confirms the final state.
        _state.update { it.copy(status = ExportStatus.CANCELLED, running = false) }
    }

    fun startOver() {
        resetSession()
    }

    fun shareIntent(): Intent? = successIntent(Intent.ACTION_SEND)

    fun viewIntent(): Intent? = successIntent(Intent.ACTION_VIEW)

    private fun successIntent(action: String): Intent? {
        val success = _state.value.result as? BurnResult.Success ?: return null
        val uri = Uri.parse(success.outputUri)
        return Intent(action).apply {
            if (action == Intent.ACTION_SEND) {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                setDataAndType(uri, "video/mp4")
            }
            clipData = ClipData.newRawUri(success.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun WorkInfo.toBurnResult(): BurnResult {
        return when (state) {
            WorkInfo.State.SUCCEEDED -> BurnResult.Success(
                outputUri = outputData.getString(BurnWorker.KEY_OUTPUT_URI).orEmpty(),
                outputPath = outputData.getString(BurnWorker.KEY_OUTPUT_PATH).orEmpty(),
                displayName = outputData.getString(BurnWorker.KEY_DISPLAY_NAME).orEmpty(),
            )
            WorkInfo.State.CANCELLED -> BurnResult.Cancelled
            WorkInfo.State.FAILED -> if (outputData.getBoolean(BurnWorker.KEY_CANCELLED, false)) {
                BurnResult.Cancelled
            } else {
                BurnResult.Failure(
                    message = outputData.getString(BurnWorker.KEY_ERROR) ?: "Burn failed",
                    exitCode = outputData.getInt(BurnWorker.KEY_EXIT, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                )
            }
            else -> BurnResult.Failure(
                message = outputData.getString(BurnWorker.KEY_ERROR) ?: "Burn failed",
                exitCode = outputData.getInt(BurnWorker.KEY_EXIT, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            )
        }
    }

    private fun WorkInfo.toStatus(progress: Int): ExportStatus {
        return when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportStatus.QUEUED
            WorkInfo.State.RUNNING -> if (progress >= 95) ExportStatus.FINISHING else ExportStatus.ENCODING
            WorkInfo.State.SUCCEEDED -> ExportStatus.SUCCESS
            WorkInfo.State.CANCELLED -> ExportStatus.CANCELLED
            WorkInfo.State.FAILED -> if (outputData.getBoolean(BurnWorker.KEY_CANCELLED, false)) {
                ExportStatus.CANCELLED
            } else {
                ExportStatus.FAILED
            }
        }
    }

    private fun BurnResult?.toStatus(): ExportStatus = when (this) {
        is BurnResult.Success -> ExportStatus.SUCCESS
        is BurnResult.Failure -> ExportStatus.FAILED
        BurnResult.Cancelled -> ExportStatus.CANCELLED
        null -> ExportStatus.FAILED
    }
}
```

## `app/src/main/java/com/burnsubtitle/ui/result/ResultViewModel.kt`

```kotlin
package com.burnsubtitle.ui.result

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.burnsubtitle.domain.model.BurnResult
import com.burnsubtitle.domain.session.BurnSession
import com.burnsubtitle.domain.usecase.ResetSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val session: BurnSession,
    private val resetSession: ResetSessionUseCase,
) : ViewModel() {
    val result: StateFlow<BurnResult?> = session.result

    fun startOver() {
        resetSession()
    }

    fun shareIntent(): Intent? = successIntent(Intent.ACTION_SEND)

    fun viewIntent(): Intent? = successIntent(Intent.ACTION_VIEW)

    private fun successIntent(action: String): Intent? {
        val success = session.result.value as? BurnResult.Success ?: return null
        val uri = Uri.parse(success.outputUri)
        return Intent(action).apply {
            if (action == Intent.ACTION_SEND) {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                setDataAndType(uri, "video/mp4")
            }
            clipData = ClipData.newRawUri(success.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
```

## `app/src/test/java/com/burnsubtitle/domain/ass/AssConversionTest.kt`

```kotlin
package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssConversionTest {
    @Test
    fun writesUnindentedSectionsForEveryCueCount() {
        val cues = (1..3).map { index ->
            SubtitleCue(
                index = index,
                startMs = index * 1000L,
                endMs = index * 1000L + 900L,
                text = "line $index\nمرحبا",
            )
        }
        val document = SubtitleDocument(cues = cues, sourceFormat = SubtitleFormat.SRT)
        val ass = AssDocumentWriter(AssStyleGenerator())
            .write(document, SubtitleStyle(), playResX = 1920, playResY = 1080)

        val body = ass.removePrefix("\uFEFF")
        // libass only recognises section headers and field names at column 0.
        body.lines().forEach { line ->
            assertTrue("indented ASS line: '$line'", line == line.trimStart())
        }
        assertTrue(body.startsWith("[Script Info]"))
        assertTrue(body.lines().contains("[V4+ Styles]"))
        assertTrue(body.lines().contains("[Events]"))
        assertEquals(3, body.lines().count { it.startsWith("Dialogue: ") })
        assertEquals(1, body.lines().count { it.startsWith("Style: Default,") })
    }

    @Test
    fun colorRoundTripsArgbToAss() {
        val white = 0xFFFFFFFFL
        val boxed = 0x99000000L
        assertEquals("&H00FFFFFF", AssColor.fromArgb(white))
        assertEquals(white, AssColor.toArgb(AssColor.fromArgb(white)))
        assertEquals(boxed, AssColor.toArgb(AssColor.fromArgb(boxed)))
    }

    @Test
    fun fontSizeScalesFrom1080pReference() {
        assertEquals(42, AssMetrics.fontSize(42, 1080))
        assertEquals(28, AssMetrics.fontSize(42, 720))
        assertEquals(84, AssMetrics.fontSize(42, 2160))
    }

    @Test
    fun positionMapsToNumpadAlignment() {
        assertEquals(2, AssAlignment.fromPosition(SubtitlePosition.BOTTOM_CENTER))
        assertEquals(7, AssAlignment.fromPosition(SubtitlePosition.TOP_LEFT))
        val margins = AssAlignment.margins(SubtitlePosition.BOTTOM_CENTER, 1920, 1080, 10)
        assertEquals(192, margins.left)
        assertEquals(108, margins.vertical)
    }
}
```

## `app/src/test/java/com/burnsubtitle/domain/parser/VttAndAssParserTest.kt`

```kotlin
package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VttParserTest {
    @Test
    fun parsesMixedArabicAndEnglish() {
        val document = VttParser().parse(
            """
            WEBVTT

            00:00:01.000 --> 00:00:02.500
            Hello <b>مرحبا</b>
            """.trimIndent(),
        )
        assertEquals(SubtitleFormat.VTT, document.sourceFormat)
        assertEquals("Hello مرحبا", document.cues.single().text)
    }

    @Test
    fun parsesCueIdentifiersAndCueSettings() {
        val document = VttParser().parse(
            """
            WEBVTT

            NOTE this file has identifiers

            intro
            00:00:01.000 --> 00:00:02.500 align:start position:10%
            First

            02
            01:02:03.040 --> 01:02:04.000 line:90%
            Second
            """.trimIndent(),
        )
        assertEquals(2, document.cues.size)
        assertEquals(1000L, document.cues[0].startMs)
        assertEquals(2500L, document.cues[0].endMs)
        assertEquals("First", document.cues[0].text)
        assertEquals(3_723_040L, document.cues[1].startMs)
        assertEquals(3_724_000L, document.cues[1].endMs)
        assertEquals("Second", document.cues[1].text)
    }
}

class AssParserTest {
    @Test
    fun parsesDialogueWithArabic() {
        val document = AssParser().parse(
            """
            [Script Info]
            Title: Test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,مرحبا{\b1} world
            """.trimIndent(),
        )
        assertEquals(1, document.cues.size)
        assertTrue(document.cues.single().text.contains("مرحبا"))
        assertTrue(document.cues.single().text.contains("world"))
        assertEquals(1000L, document.cues.single().startMs)
    }
}
```

## `app/src/test/java/com/burnsubtitle/domain/subtitle/SubtitleDecoderTest.kt`

```kotlin
package com.burnsubtitle.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SubtitleDecoderTest {

    @Test
    fun decodesUtf8WithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "مرحبا".toByteArray(Charsets.UTF_8)
        assertEquals("مرحبا", SubtitleDecoder.decode(bytes))
    }

    @Test
    fun decodesUtf16WithBomAndDropsIt() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Hello مرحبا".toByteArray(Charsets.UTF_16LE)
        assertEquals("Hello مرحبا", SubtitleDecoder.decode(bytes))
    }

    @Test
    fun decodesPlainUtf8WithoutBom() {
        assertEquals(
            "Hello مرحبا",
            SubtitleDecoder.decode("Hello مرحبا".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun decodesLegacyArabicWindows1256() {
        val text = "مرحبا بالعالم"
        val bytes = text.toByteArray(Charset.forName("windows-1256"))
        // The same bytes are not valid UTF-8, so a naive UTF-8 decode loses the text.
        assertTrue(String(bytes, Charsets.UTF_8).contains('\uFFFD'))
        assertEquals(text, SubtitleDecoder.decode(bytes))
    }

    @Test
    fun decodesBomlessUtf16Little() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nHello مرحبا\n"
        val bytes = text.toByteArray(Charsets.UTF_16LE)
        // These bytes are legal UTF-8, so a strict UTF-8 decode yields embedded NULs.
        assertTrue(String(bytes, Charsets.UTF_8).contains('\u0000'))
        assertEquals(text, SubtitleDecoder.decode(bytes))
    }

    @Test
    fun decodesBomlessUtf16Big() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nHello there\n"
        assertEquals(text, SubtitleDecoder.decode(text.toByteArray(Charsets.UTF_16BE)))
    }

    @Test
    fun keepsAsciiIntactForLegacyLatinBytes() {
        val bytes = "Caf\u00E9 time".toByteArray(Charset.forName("windows-1252"))
        assertEquals("Café time", SubtitleDecoder.decode(bytes))
    }
}
```

## `app/src/test/java/com/burnsubtitle/domain/util/MediaFileNamesTest.kt`

```kotlin
package com.burnsubtitle.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileNamesTest {
    @Test
    fun extensionIsLowercase() {
        assertEquals("mkv", MediaFileNames.extension("Holiday.MKV"))
        assertEquals("", MediaFileNames.extension("no-extension"))
    }

    @Test
    fun detectsVideoFromMimeOrExtension() {
        assertTrue(MediaFileNames.isVideoFile("clip.bin", "video/mp4"))
        assertTrue(MediaFileNames.isVideoFile("clip.mkv", "application/octet-stream"))
        assertTrue(MediaFileNames.isVideoFile("clip.mp4", null))
        assertFalse(MediaFileNames.isVideoFile("photo.jpg", "image/jpeg"))
        assertFalse(MediaFileNames.isVideoFile("song.mp3", "audio/mpeg"))
        assertFalse(MediaFileNames.isVideoFile("subs.srt", "text/plain"))
    }

    @Test
    fun sanitizeStripsPathSeparatorsAndKeepsUnicode() {
        assertEquals("a_b_c", MediaFileNames.sanitize("a/b\\c"))
        assertEquals("clip_2", MediaFileNames.sanitize("clip:2"))
        assertEquals("فيلم عربي", MediaFileNames.sanitize("فيلم عربي"))
        assertEquals("video", MediaFileNames.sanitize("   "))
        // Traversal collapses to a harmless name instead of escaping the export directory.
        assertEquals("_", MediaFileNames.sanitize("../.."))
        assertEquals(120, MediaFileNames.sanitize("x".repeat(400)).length)
    }
}
```

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false

android.useAndroidX=true
android.nonTransitiveRClass=true
# KSP (2.2.10-2.0.2) still registers generated sources via kotlin.sourceSets, which
# AGP 9's built-in Kotlin rejects. Remove this once KSP ships a fix for AGP 9
# (tracked in https://github.com/google/ksp/issues/2729, fixed for KSP 2.3.4+).
android.disallowKotlinSourceSets=false

kotlin.code.style=official
kotlin.incremental=true
```

## `native/CMakeLists.txt`

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(burnffmpeg)

add_library(burnffmpeg SHARED
    jni/ffmpeg_jni.cpp
    jni/log_bridge.cpp
)

find_library(log-lib log)
find_library(android-lib android)
target_include_directories(burnffmpeg PRIVATE jni)

set(FFMPEG_PREFIX "${CMAKE_CURRENT_SOURCE_DIR}/out/prefix/${ANDROID_ABI}")
set(FFMPEG_PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt")
set(FFMPEG_INCLUDE "")
if (EXISTS "${FFMPEG_PREFIX}/include/libavformat/avformat.h")
    set(FFMPEG_INCLUDE "${FFMPEG_PREFIX}/include")
    set(FFMPEG_LIB_DIR "${FFMPEG_PREFIX}/lib")
elseif (EXISTS "${FFMPEG_PREBUILT}/include/libavformat/avformat.h")
    set(FFMPEG_INCLUDE "${FFMPEG_PREBUILT}/include")
    if (EXISTS "${FFMPEG_PREBUILT}/${ANDROID_ABI}")
        set(FFMPEG_LIB_DIR "${FFMPEG_PREBUILT}/${ANDROID_ABI}")
    endif()
endif()

if (FFMPEG_INCLUDE AND EXISTS "${FFMPEG_INCLUDE}/libavformat/avformat.h")
    message(STATUS "Linking bundled FFmpeg from ${FFMPEG_INCLUDE}")
    target_sources(burnffmpeg PRIVATE jni/ffmpeg_burn.cpp)
    target_compile_definitions(burnffmpeg PRIVATE BURN_HAVE_FFMPEG=1)
    target_include_directories(burnffmpeg PRIVATE "${FFMPEG_INCLUDE}")
    # Only the FFmpeg libraries themselves. libass, HarfBuzz, FreeType, FriBidi and x264 are
    # static archives already linked into libavfilter.so, so linking them here again would
    # duplicate their symbols inside this module.
    set(FFMPEG_SO_NAMES avfilter avformat avcodec swscale swresample avutil)
    foreach (libname IN LISTS FFMPEG_SO_NAMES)
        set(so_path "")
        if (FFMPEG_LIB_DIR AND EXISTS "${FFMPEG_LIB_DIR}/lib${libname}.so")
            set(so_path "${FFMPEG_LIB_DIR}/lib${libname}.so")
        elseif (EXISTS "${CMAKE_SOURCE_DIR}/../app/src/main/jniLibs/${ANDROID_ABI}/lib${libname}.so")
            set(so_path "${CMAKE_SOURCE_DIR}/../app/src/main/jniLibs/${ANDROID_ABI}/lib${libname}.so")
        endif()
        if (NOT so_path)
            message(FATAL_ERROR "FFmpeg headers found but lib${libname}.so is missing for ${ANDROID_ABI}")
        endif()
        add_library(${libname} SHARED IMPORTED)
        set_target_properties(${libname} PROPERTIES IMPORTED_LOCATION "${so_path}")
        target_link_libraries(burnffmpeg ${libname})
    endforeach()
else()
    message(STATUS "FFmpeg headers not found; building JNI stub. CI native build supplies the real libraries.")
endif()

target_link_libraries(burnffmpeg ${log-lib} ${android-lib})
# Required for devices with 16 KB memory pages (Android 15+ on arm64).
if (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64")
    target_link_options(burnffmpeg PRIVATE "-Wl,-z,max-page-size=16384")
endif()
if (ANDROID_ABI STREQUAL "armeabi-v7a")
    target_link_libraries(burnffmpeg atomic)
endif()
```

## `native/jni/ffmpeg_burn.cpp`

```cpp
#ifdef BURN_HAVE_FFMPEG

#include "ffmpeg_burn.h"
#include "log_bridge.h"

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavcodec/bsf.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersink.h>
#include <libavfilter/buffersrc.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/display.h>
#include <libavutil/opt.h>
#include <libavutil/pixfmt.h>
#include <libavutil/time.h>
}

#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

namespace {

constexpr int kCancelled = 255;
constexpr int kError = 1;

struct Pipeline {
    AVFormatContext *in{};
    AVFormatContext *out{};
    AVCodecContext *decoder{};
    AVCodecContext *encoder{};
    AVFilterGraph *graph{};
    AVFilterContext *src{};
    AVFilterContext *sink{};
    AVPacket *ipkt{};
    AVPacket *opkt{};
    AVFrame *frame{};
    AVFrame *filtered{};
    AVBSFContext *audio_bsf{};
    int video_in = -1;
    int audio_in = -1;
    int video_out = -1;
    int audio_out = -1;
};

int interrupt_cb(void * /* opaque */) {
    return burn_is_cancelled();
}

void log_cb(void *ptr, int level, const char *fmt, va_list vl) {
    if (level > av_log_get_level()) {
        return;
    }
    char line[1024];
    int print_prefix = 1;
    av_log_format_line(ptr, level, fmt, vl, line, sizeof(line), &print_prefix);
    size_t n = strlen(line);
    while (n > 0 && (line[n - 1] == '\n' || line[n - 1] == '\r')) {
        line[--n] = 0;
    }
    if (n > 0) {
        burn_forward_log(line);
    }
}

// Mirrors fftools' autorotate handling: phone recordings are stored landscape with a
// display matrix, so the frame has to be rotated before subtitles are burned in.
// Otherwise the export is sideways and the text is rotated with it.
std::string autorotate_prefix(AVStream *st, bool *swaps_dimensions) {
    *swaps_dimensions = false;
    const AVPacketSideData *sd = av_packet_side_data_get(
            st->codecpar->coded_side_data,
            st->codecpar->nb_coded_side_data,
            AV_PKT_DATA_DISPLAYMATRIX);
    if (sd == nullptr || sd->size < 9 * sizeof(int32_t)) {
        return "";
    }
    const int32_t *matrix = reinterpret_cast<const int32_t *>(sd->data);
    double theta = -std::round(av_display_rotation_get(matrix));
    theta -= 360 * std::floor(theta / 360 + 0.9 / 360);
    if (std::fabs(theta - 90) < 1.0) {
        *swaps_dimensions = true;
        return matrix[3] > 0 ? "transpose=cclock_flip," : "transpose=clock,";
    }
    if (std::fabs(theta - 180) < 1.0) {
        std::string filters;
        if (matrix[0] < 0) filters += "hflip,";
        if (matrix[4] < 0) filters += "vflip,";
        return filters;
    }
    if (std::fabs(theta - 270) < 1.0) {
        *swaps_dimensions = true;
        return matrix[3] < 0 ? "transpose=clock_flip," : "transpose=cclock,";
    }
    return "";
}

std::string escape_filter_path(const char *path) {
    std::string out;
    out.reserve(strlen(path) + 8);
    for (const char *p = path; *p; ++p) {
        if (*p == '\\') {
            out += '/';
        } else {
            if (*p == ':' || *p == '\'' || *p == '[' || *p == ']' || *p == ',' || *p == ';') {
                out += '\\';
            }
            out += *p;
        }
    }
    return out;
}

void close_pipeline(Pipeline *p) {
    av_bsf_free(&p->audio_bsf);
    avfilter_graph_free(&p->graph);
    avcodec_free_context(&p->decoder);
    avcodec_free_context(&p->encoder);
    if (p->out) {
        if (!(p->out->oformat->flags & AVFMT_NOFILE) && p->out->pb) {
            avio_closep(&p->out->pb);
        }
        avformat_free_context(p->out);
        p->out = nullptr;
    }
    if (p->in) {
        avformat_close_input(&p->in);
    }
    av_packet_free(&p->ipkt);
    av_packet_free(&p->opkt);
    av_frame_free(&p->frame);
    av_frame_free(&p->filtered);
}

int open_input(Pipeline *p, const char *path) {
    p->in = nullptr;
    AVDictionary *opts = nullptr;
    av_dict_set(&opts, "scan_all_pmts", "1", 0);
    int err = avformat_open_input(&p->in, path, nullptr, &opts);
    av_dict_free(&opts);
    if (err < 0) {
        return err;
    }
    p->in->interrupt_callback.callback = interrupt_cb;
    err = avformat_find_stream_info(p->in, nullptr);
    if (err < 0) {
        return err;
    }
    p->video_in = av_find_best_stream(p->in, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    p->audio_in = av_find_best_stream(p->in, AVMEDIA_TYPE_AUDIO, -1, p->video_in, nullptr, 0);
    if (p->video_in < 0) {
        return AVERROR_STREAM_NOT_FOUND;
    }
    return 0;
}

int open_decoder(Pipeline *p) {
    AVStream *st = p->in->streams[p->video_in];
    const AVCodec *codec = avcodec_find_decoder(st->codecpar->codec_id);
    if (!codec) {
        return AVERROR_DECODER_NOT_FOUND;
    }
    p->decoder = avcodec_alloc_context3(codec);
    if (!p->decoder) {
        return AVERROR(ENOMEM);
    }
    int err = avcodec_parameters_to_context(p->decoder, st->codecpar);
    if (err < 0) {
        return err;
    }
    p->decoder->pkt_timebase = st->time_base;
    err = avcodec_open2(p->decoder, codec, nullptr);
    return err;
}

int build_filters(Pipeline *p, const char *ass, const char *fonts) {
    p->graph = avfilter_graph_alloc();
    if (!p->graph) {
        return AVERROR(ENOMEM);
    }
    const AVFilter *buffersrc = avfilter_get_by_name("buffer");
    const AVFilter *buffersink = avfilter_get_by_name("buffersink");
    if (!buffersrc || !buffersink) {
        return AVERROR_FILTER_NOT_FOUND;
    }
    char args[320];
    // Decoded frames carry PTS in the input stream's time base, not in the decoder's
    // (unset) time_base. Feeding buffersrc the wrong unit shifts every subtitle.
    AVStream *in_stream = p->in->streams[p->video_in];
    AVRational tb = in_stream->time_base;
    if (tb.num <= 0 || tb.den <= 0) {
        tb = p->decoder->pkt_timebase.num > 0 ? p->decoder->pkt_timebase : av_make_q(1, AV_TIME_BASE);
    }
    AVRational sar = p->decoder->sample_aspect_ratio.num ? p->decoder->sample_aspect_ratio : av_make_q(1, 1);
    AVRational fps = av_guess_frame_rate(p->in, in_stream, nullptr);
    if (fps.num <= 0 || fps.den <= 0) {
        fps = av_make_q(0, 1);
    }
    snprintf(
            args,
            sizeof(args),
            "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d:frame_rate=%d/%d",
            p->decoder->width,
            p->decoder->height,
            p->decoder->pix_fmt,
            tb.num,
            tb.den,
            sar.num,
            sar.den,
            fps.num,
            fps.den);
    int err = avfilter_graph_create_filter(&p->src, buffersrc, "in", args, nullptr, p->graph);
    if (err < 0) {
        return err;
    }
    err = avfilter_graph_create_filter(&p->sink, buffersink, "out", nullptr, nullptr, p->graph);
    if (err < 0) {
        return err;
    }
    enum AVPixelFormat pix[] = {AV_PIX_FMT_YUV420P, AV_PIX_FMT_NONE};
    err = av_opt_set_int_list(p->sink, "pix_fmts", pix, AV_PIX_FMT_NONE, AV_OPT_SEARCH_CHILDREN);
    if (err < 0) {
        return err;
    }
    bool rotation_swaps_dimensions = false;
    std::string filt = autorotate_prefix(in_stream, &rotation_swaps_dimensions);
    // original_size must describe the frame the subtitles are drawn onto, which is the
    // upright frame produced by the rotation filters above.
    const int upright_w = rotation_swaps_dimensions ? p->decoder->height : p->decoder->width;
    const int upright_h = rotation_swaps_dimensions ? p->decoder->width : p->decoder->height;
    filt += "subtitles=filename=";
    filt += escape_filter_path(ass);
    filt += ":fontsdir=";
    filt += escape_filter_path(fonts);
    filt += ":charenc=UTF-8:original_size=";
    filt += std::to_string(upright_w);
    filt += "x";
    filt += std::to_string(upright_h);
    filt += ",format=yuv420p";
    burn_forward_log(("filter: " + filt).c_str());
    AVFilterInOut *outputs = avfilter_inout_alloc();
    AVFilterInOut *inputs = avfilter_inout_alloc();
    if (!outputs || !inputs) {
        avfilter_inout_free(&outputs);
        avfilter_inout_free(&inputs);
        return AVERROR(ENOMEM);
    }
    outputs->name = av_strdup("in");
    outputs->filter_ctx = p->src;
    outputs->pad_idx = 0;
    outputs->next = nullptr;
    inputs->name = av_strdup("out");
    inputs->filter_ctx = p->sink;
    inputs->pad_idx = 0;
    inputs->next = nullptr;
    err = avfilter_graph_parse_ptr(p->graph, filt.c_str(), &inputs, &outputs, nullptr);
    avfilter_inout_free(&outputs);
    avfilter_inout_free(&inputs);
    if (err < 0) {
        return err;
    }
    return avfilter_graph_config(p->graph, nullptr);
}

int alloc_output(Pipeline *p, const char *path) {
    return avformat_alloc_output_context2(&p->out, nullptr, "mp4", path);
}

int open_encoder(Pipeline *p, int crf, const char *preset) {
    // Only libx264 is supported. A generic H.264 lookup can land on h264_mediacodec,
    // which needs hardware frame contexts this synchronous pipeline never sets up.
    const AVCodec *codec = avcodec_find_encoder_by_name("libx264");
    if (!codec) {
        return AVERROR_ENCODER_NOT_FOUND;
    }
    p->encoder = avcodec_alloc_context3(codec);
    if (!p->encoder) {
        return AVERROR(ENOMEM);
    }
    p->encoder->width = av_buffersink_get_w(p->sink);
    p->encoder->height = av_buffersink_get_h(p->sink);
    p->encoder->pix_fmt = AV_PIX_FMT_YUV420P;
    p->encoder->time_base = av_buffersink_get_time_base(p->sink);
    if (p->encoder->time_base.num <= 0 || p->encoder->time_base.den <= 0) {
        p->encoder->time_base = av_make_q(1, 90000);
    }
    p->encoder->framerate = av_buffersink_get_frame_rate(p->sink);
    p->encoder->sample_aspect_ratio = av_buffersink_get_sample_aspect_ratio(p->sink);
    p->encoder->gop_size = 48;
    p->encoder->max_b_frames = 2;
    if (p->out && p->out->oformat && (p->out->oformat->flags & AVFMT_GLOBALHEADER)) {
        p->encoder->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;
    }
    AVDictionary *opts = nullptr;
    char crf_value[8];
    snprintf(crf_value, sizeof(crf_value), "%d", crf);
    av_dict_set(&opts, "crf", crf_value, 0);
    av_dict_set(&opts, "preset", preset && preset[0] ? preset : "veryfast", 0);
    int err = avcodec_open2(p->encoder, codec, &opts);
    av_dict_free(&opts);
    return err;
}

/**
 * MPEG-TS and some MKV files carry AAC with ADTS headers, which the MP4 muxer rejects.
 * Running those packets through aac_adtstoasc converts them to the raw form MP4 expects
 * and produces the codec extradata the output stream needs.
 */
int init_audio_bsf(Pipeline *p, const AVStream *in_audio, AVStream *out_audio) {
    if (in_audio->codecpar->codec_id != AV_CODEC_ID_AAC) {
        return 0;
    }
    const AVBitStreamFilter *filter = av_bsf_get_by_name("aac_adtstoasc");
    if (!filter) {
        return 0;
    }
    int err = av_bsf_alloc(filter, &p->audio_bsf);
    if (err < 0) {
        return err;
    }
    err = avcodec_parameters_copy(p->audio_bsf->par_in, in_audio->codecpar);
    if (err < 0) {
        return err;
    }
    p->audio_bsf->time_base_in = in_audio->time_base;
    err = av_bsf_init(p->audio_bsf);
    if (err < 0) {
        return err;
    }
    err = avcodec_parameters_copy(out_audio->codecpar, p->audio_bsf->par_out);
    if (err < 0) {
        return err;
    }
    out_audio->codecpar->codec_tag = 0;
    return 0;
}

int write_audio_packet(Pipeline *p, AVPacket *pkt) {
    AVStream *out_audio = p->out->streams[p->audio_out];
    if (!p->audio_bsf) {
        av_packet_rescale_ts(pkt, p->in->streams[p->audio_in]->time_base, out_audio->time_base);
        pkt->stream_index = p->audio_out;
        return av_interleaved_write_frame(p->out, pkt);
    }
    int err = av_bsf_send_packet(p->audio_bsf, pkt);
    if (err < 0) {
        return err;
    }
    while (true) {
        err = av_bsf_receive_packet(p->audio_bsf, pkt);
        if (err == AVERROR(EAGAIN) || err == AVERROR_EOF) {
            return 0;
        }
        if (err < 0) {
            return err;
        }
        av_packet_rescale_ts(pkt, p->audio_bsf->time_base_out, out_audio->time_base);
        pkt->stream_index = p->audio_out;
        err = av_interleaved_write_frame(p->out, pkt);
        av_packet_unref(pkt);
        if (err < 0) {
            return err;
        }
    }
}

int write_output_header(Pipeline *p, const char *path) {
    AVStream *vs = avformat_new_stream(p->out, nullptr);
    if (!vs) {
        return AVERROR(ENOMEM);
    }
    p->video_out = vs->index;
    int err = avcodec_parameters_from_context(vs->codecpar, p->encoder);
    if (err < 0) {
        return err;
    }
    vs->time_base = p->encoder->time_base;
    if (p->audio_in >= 0) {
        AVStream *in_audio = p->in->streams[p->audio_in];
        AVStream *as = avformat_new_stream(p->out, nullptr);
        if (!as) {
            return AVERROR(ENOMEM);
        }
        p->audio_out = as->index;
        err = avcodec_parameters_copy(as->codecpar, in_audio->codecpar);
        if (err < 0) {
            return err;
        }
        as->codecpar->codec_tag = 0;
        as->time_base = in_audio->time_base;
        err = init_audio_bsf(p, in_audio, as);
        if (err < 0) {
            return err;
        }
    }
    if (!(p->out->oformat->flags & AVFMT_NOFILE)) {
        err = avio_open(&p->out->pb, path, AVIO_FLAG_WRITE);
        if (err < 0) {
            return err;
        }
    }
    AVDictionary *opts = nullptr;
    av_dict_set(&opts, "movflags", "+faststart", 0);
    err = avformat_write_header(p->out, &opts);
    av_dict_free(&opts);
    return err;
}

int encode_frame(Pipeline *p, AVFrame *frame) {
    int err = avcodec_send_frame(p->encoder, frame);
    if (err < 0) {
        return err;
    }
    while (err >= 0) {
        err = avcodec_receive_packet(p->encoder, p->opkt);
        if (err == AVERROR(EAGAIN) || err == AVERROR_EOF) {
            return 0;
        }
        if (err < 0) {
            return err;
        }
        av_packet_rescale_ts(p->opkt, p->encoder->time_base, p->out->streams[p->video_out]->time_base);
        p->opkt->stream_index = p->video_out;
        err = av_interleaved_write_frame(p->out, p->opkt);
        av_packet_unref(p->opkt);
        if (err < 0) {
            return err;
        }
    }
    return 0;
}

int filter_and_encode(Pipeline *p, AVFrame *in, long duration_ms) {
    int err = av_buffersrc_add_frame_flags(p->src, in, AV_BUFFERSRC_FLAG_KEEP_REF);
    if (err < 0) {
        return err;
    }
    while (true) {
        if (burn_is_cancelled()) {
            return AVERROR_EXIT;
        }
        err = av_buffersink_get_frame(p->sink, p->filtered);
        if (err == AVERROR(EAGAIN) || err == AVERROR_EOF) {
            return 0;
        }
        if (err < 0) {
            return err;
        }
        if (p->filtered->pts != AV_NOPTS_VALUE) {
            const AVRational tb = av_buffersink_get_time_base(p->sink);
            const long time_ms = static_cast<long>(p->filtered->pts * av_q2d(tb) * 1000.0);
            burn_forward_progress(time_ms);
            (void) duration_ms;
        }
        err = encode_frame(p, p->filtered);
        av_frame_unref(p->filtered);
        if (err < 0) {
            return err;
        }
    }
}

int process(Pipeline *p, long duration_ms) {
    int err;
    while ((err = av_read_frame(p->in, p->ipkt)) >= 0) {
        if (burn_is_cancelled()) {
            av_packet_unref(p->ipkt);
            return AVERROR_EXIT;
        }
        if (p->ipkt->stream_index == p->video_in) {
            err = avcodec_send_packet(p->decoder, p->ipkt);
            av_packet_unref(p->ipkt);
            if (err < 0) {
                return err;
            }
            while (true) {
                err = avcodec_receive_frame(p->decoder, p->frame);
                if (err == AVERROR(EAGAIN) || err == AVERROR_EOF) {
                    break;
                }
                if (err < 0) {
                    return err;
                }
                err = filter_and_encode(p, p->frame, duration_ms);
                av_frame_unref(p->frame);
                if (err < 0) {
                    return err;
                }
            }
        } else if (p->audio_out >= 0 && p->ipkt->stream_index == p->audio_in) {
            err = write_audio_packet(p, p->ipkt);
            av_packet_unref(p->ipkt);
            if (err < 0) {
                return err;
            }
        } else {
            av_packet_unref(p->ipkt);
        }
    }
    if (err != AVERROR_EOF && err < 0) {
        return err;
    }
    avcodec_send_packet(p->decoder, nullptr);
    while (avcodec_receive_frame(p->decoder, p->frame) >= 0) {
        err = filter_and_encode(p, p->frame, duration_ms);
        av_frame_unref(p->frame);
        if (err < 0) {
            return err;
        }
    }
    err = av_buffersrc_add_frame_flags(p->src, nullptr, 0);
    if (err < 0) {
        return err;
    }
    while (av_buffersink_get_frame(p->sink, p->filtered) >= 0) {
        err = encode_frame(p, p->filtered);
        av_frame_unref(p->filtered);
        if (err < 0) {
            return err;
        }
    }
    err = encode_frame(p, nullptr);
    if (err < 0) {
        return err;
    }
    return av_write_trailer(p->out);
}

}  // namespace

int burn_subtitles(
        const char *input_path,
        const char *output_path,
        const char *ass_path,
        const char *fonts_dir,
        const char *fontconfig_path,
        int crf,
        const char *preset,
        long duration_ms) {
    av_log_set_callback(log_cb);
    av_log_set_level(AV_LOG_INFO);
    if (fontconfig_path && fontconfig_path[0]) {
        setenv("FONTCONFIG_FILE", fontconfig_path, 1);
    }
    burn_forward_log("Starting subtitle burn");
    Pipeline p{};
    p.ipkt = av_packet_alloc();
    p.opkt = av_packet_alloc();
    p.frame = av_frame_alloc();
    p.filtered = av_frame_alloc();
    int err = 0;
    if (!p.ipkt || !p.opkt || !p.frame || !p.filtered) {
        err = AVERROR(ENOMEM);
        goto done;
    }
    err = open_input(&p, input_path);
    if (err < 0) goto done;
    err = open_decoder(&p);
    if (err < 0) goto done;
    err = build_filters(&p, ass_path, fonts_dir);
    if (err < 0) goto done;
    err = alloc_output(&p, output_path);
    if (err < 0) goto done;
    p.out->interrupt_callback.callback = interrupt_cb;
    err = open_encoder(&p, crf, preset);
    if (err < 0) goto done;
    err = write_output_header(&p, output_path);
    if (err < 0) goto done;
    err = process(&p, duration_ms);

done:
    close_pipeline(&p);
    if (burn_is_cancelled()) {
        burn_forward_log("Burn cancelled");
        return kCancelled;
    }
    if (err < 0) {
        char buf[128];
        av_strerror(err, buf, sizeof(buf));
        burn_forward_log(buf);
        return kError;
    }
    burn_forward_log("Burn finished");
    burn_forward_progress(duration_ms > 0 ? duration_ms : 0);
    return 0;
}

#endif
```

## `native/jni/ffmpeg_jni.cpp`

```cpp
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
```

## `native/scripts/build-ffmpeg.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOCK="${ROOT}/third_party/ffmpeg.lock"
OUT="${ROOT}/native/out"
JNI_LIBS="${ROOT}/app/src/main/jniLibs"

source_lock() {
  while IFS='=' read -r key value; do
    [[ -z "${key}" || "${key}" =~ ^# ]] && continue
    printf -v "${key}" '%s' "${value}"
  done < "${LOCK}"
}

source_lock

NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "${NDK_HOME}" ]]; then
  echo "ANDROID_NDK_HOME is required" >&2
  exit 1
fi

API=26
JOBS="$(nproc)"
mkdir -p "${OUT}/src" "${OUT}/prefix"

clone_at() {
  local url="$1"
  local ref="$2"
  local dest="$3"
  if [[ ! -d "${dest}/.git" ]]; then
    git clone --depth 1 --branch "${ref}" "${url}" "${dest}" || {
      git clone "${url}" "${dest}"
      git -C "${dest}" checkout "${ref}"
    }
  fi
}

clone_at "${freetype_url}" "${freetype_ref}" "${OUT}/src/freetype"
clone_at "${harfbuzz_url}" "${harfbuzz_ref}" "${OUT}/src/harfbuzz"
clone_at "${fribidi_url}" "${fribidi_ref}" "${OUT}/src/fribidi"
clone_at "${libass_url}" "${libass_ref}" "${OUT}/src/libass"
clone_at "${x264_url}" "${x264_ref}" "${OUT}/src/x264"
clone_at "${ffmpeg_url}" "${ffmpeg_ref}" "${OUT}/src/ffmpeg"

IFS=',' read -r -a ABI_LIST <<< "${abis}"

host_tag() {
  case "$(uname -s)" in
    Darwin) echo "darwin-x86_64" ;;
    *) echo "linux-x86_64" ;;
  esac
}

abi_triple() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "armv7a-linux-androideabi" ;;
    x86_64) echo "x86_64-linux-android" ;;
    *) echo "unsupported ABI $1" >&2; return 1 ;;
  esac
}

# config.sub rejects NDK's armv7a-* clang prefix; autotools still uses this --host.
autotools_host() {
  case "$1" in
    armeabi-v7a) echo "arm-linux-androideabi" ;;
    *) abi_triple "$1" ;;
  esac
}

TOOLCHAIN="${NDK_HOME}/toolchains/llvm/prebuilt/$(host_tag)"
SYSROOT="${TOOLCHAIN}/sysroot"
export PATH="${TOOLCHAIN}/bin:${PATH}"

# FriBidi gen.tab (and similar) must compile and run on the build machine.
export CC_FOR_BUILD="${CC_FOR_BUILD:-cc}"
export CXX_FOR_BUILD="${CXX_FOR_BUILD:-c++}"
export CFLAGS_FOR_BUILD="${CFLAGS_FOR_BUILD:--O2}"
export LDFLAGS_FOR_BUILD="${LDFLAGS_FOR_BUILD:-}"
BUILD_TRIPLE="$("${CC_FOR_BUILD}" -dumpmachine)"

write_meson_cross() {
  local abi="$1"
  local triple="$2"
  local prefix="$3"
  local cpu_family cpu
  case "${abi}" in
    arm64-v8a) cpu_family="aarch64"; cpu="aarch64" ;;
    armeabi-v7a) cpu_family="arm"; cpu="armv7" ;;
    *) cpu_family="x86_64"; cpu="x86_64" ;;
  esac
  local cross="${OUT}/meson-android-${abi}.ini"
  cat > "${cross}" <<EOF
[binaries]
c = '${CC}'
cpp = '${CXX}'
ar = '${AR}'
strip = '${STRIP}'
pkg-config = 'pkg-config'

[built-in options]
c_args = ['-fPIC', '-O2']
cpp_args = ['-fPIC', '-O2']
c_link_args = ['-fPIC', '-Wl,-z,max-page-size=16384']
cpp_link_args = ['-fPIC', '-Wl,-z,max-page-size=16384']
prefix = '${prefix}'

[host_machine]
system = 'android'
cpu_family = '${cpu_family}'
cpu = '${cpu}'
endian = 'little'
EOF
  echo "${cross}"
}

for ABI in "${ABI_LIST[@]}"; do
  TRIPLE="$(abi_triple "${ABI}")"
  HOST="$(autotools_host "${ABI}")"
  PREFIX="${OUT}/prefix/${ABI}"
  mkdir -p "${PREFIX}"
  export CC="${TOOLCHAIN}/bin/${TRIPLE}${API}-clang"
  export CXX="${TOOLCHAIN}/bin/${TRIPLE}${API}-clang++"
  export AR="${TOOLCHAIN}/bin/llvm-ar"
  export RANLIB="${TOOLCHAIN}/bin/llvm-ranlib"
  export STRIP="${TOOLCHAIN}/bin/llvm-strip"
  export NM="${TOOLCHAIN}/bin/llvm-nm"
  export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig"
  export PKG_CONFIG_LIBDIR="${PREFIX}/lib/pkgconfig"
  unset PKG_CONFIG_SYSROOT_DIR || true
  export CFLAGS="-fPIC -O2"
  export CXXFLAGS="-fPIC -O2"
  export LDFLAGS="-fPIC -Wl,-z,max-page-size=16384"
  MESON_CROSS="$(write_meson_cross "${ABI}" "${TRIPLE}" "${PREFIX}")"

  if [[ ! -f "${PREFIX}/lib/libfreetype.a" ]]; then
    pushd "${OUT}/src/freetype" >/dev/null
    make distclean || true
    ./autogen.sh || true
    ./configure --build="${BUILD_TRIPLE}" --host="${HOST}" --prefix="${PREFIX}" --enable-static --disable-shared --with-png=no --with-harfbuzz=no --with-bzip2=no --with-brotli=no
    make -j"${JOBS}"
    make install
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libfreetype.a"

  if [[ ! -f "${PREFIX}/lib/libfribidi.a" ]]; then
    pushd "${OUT}/src/fribidi" >/dev/null
    rm -rf "build-${ABI}"
    meson setup "build-${ABI}" \
      --cross-file "${MESON_CROSS}" \
      --prefix "${PREFIX}" \
      --default-library static \
      -Ddocs=false \
      -Dbin=false \
      -Dtests=false
    meson compile -C "build-${ABI}"
    meson install -C "build-${ABI}"
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libfribidi.a"

  # libass shapes through hb-ft, so HarfBuzz must see the FreeType we just installed.
  if [[ ! -f "${PREFIX}/lib/libharfbuzz.a" ]]; then
    pushd "${OUT}/src/harfbuzz" >/dev/null
    rm -rf "build-${ABI}"
    meson setup "build-${ABI}" \
      --cross-file "${MESON_CROSS}" \
      --prefix "${PREFIX}" \
      --default-library static \
      -Dtests=disabled \
      -Ddocs=disabled \
      -Dutilities=disabled \
      -Dfreetype=enabled \
      -Dglib=disabled \
      -Dcairo=disabled \
      -Dgobject=disabled \
      -Dicu=disabled
    meson compile -C "build-${ABI}"
    meson install -C "build-${ABI}"
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libharfbuzz.a"

  if [[ ! -f "${PREFIX}/lib/libass.a" ]]; then
    pushd "${OUT}/src/libass" >/dev/null
    make distclean || true
    ./autogen.sh
    ./configure --build="${BUILD_TRIPLE}" --host="${HOST}" --prefix="${PREFIX}" --enable-static --disable-shared --disable-require-system-font-provider
    make -j"${JOBS}"
    make install
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libass.a"

  if [[ ! -f "${PREFIX}/lib/libx264.a" ]]; then
    pushd "${OUT}/src/x264" >/dev/null
    make distclean || true
    ./configure --host="${HOST}" --prefix="${PREFIX}" --enable-static --enable-pic --disable-cli --disable-opencl --sysroot="${SYSROOT}" --cross-prefix="${TOOLCHAIN}/bin/llvm-" --extra-cflags="-fPIC" --extra-ldflags="-Wl,-z,max-page-size=16384"
    make -j"${JOBS}"
    make install
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libx264.a"

  if [[ -f "${PREFIX}/lib/libavformat.so" ]]; then
    echo "Skipping FFmpeg configure for ${ABI} (already installed)"
  else
    pushd "${OUT}/src/ffmpeg" >/dev/null
    if [[ -f ffbuild/config.mak ]]; then
      make distclean || true
    fi
    pkg-config --exists --print-errors x264
    ./configure \
      --prefix="${PREFIX}" \
      --target-os=android \
      --arch="$([[ ${ABI} == arm64-v8a ]] && echo aarch64 || echo arm)" \
      --cpu="$([[ ${ABI} == arm64-v8a ]] && echo armv8-a || echo armv7-a)" \
      --enable-cross-compile \
      --pkg-config="$(command -v pkg-config)" \
      --cc="${CC}" \
      --cxx="${CXX}" \
      --ar="${AR}" \
      --nm="${NM}" \
      --ranlib="${RANLIB}" \
      --strip="${STRIP}" \
      --sysroot="${SYSROOT}" \
      --enable-gpl \
      --enable-pic \
      --enable-libass \
      --enable-libfreetype \
      --enable-libharfbuzz \
      --enable-libfribidi \
      --enable-libx264 \
      --enable-encoder=h264_mediacodec \
      --enable-decoder=h264_mediacodec \
      --enable-jni \
      --enable-mediacodec \
      --enable-shared \
      --disable-static \
      --disable-doc \
      --disable-programs \
      --disable-debug \
      --extra-cflags="-I${PREFIX}/include -fPIC" \
      --extra-ldflags="-L${PREFIX}/lib -lm -lz -landroid -llog -Wl,-z,max-page-size=16384" \
      --extra-libs="-lc++_shared" \
      --pkg-config-flags="--static"
    make -j"${JOBS}"
    make install
    popd >/dev/null
  fi
  test -f "${PREFIX}/lib/libavformat.so"
done

"${ROOT}/native/scripts/package-jniLibs.sh"
```

## `native/scripts/package-jniLibs.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREFIX_ROOT="${ROOT}/native/out/prefix"
DEST="${ROOT}/app/src/main/jniLibs"
HEADERS="${ROOT}/native/prebuilt/include"
NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

host_tag() {
  case "$(uname -s)" in
    Darwin) echo "darwin-x86_64" ;;
    *) echo "linux-x86_64" ;;
  esac
}

# The NDK sysroot uses the plain "arm-linux-androideabi" directory for 32-bit ARM,
# not the "armv7a-" clang prefix, so libc++_shared.so lives under this name.
sysroot_triple() {
  case "$1" in
    arm64-v8a) echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "arm-linux-androideabi" ;;
    x86_64) echo "x86_64-linux-android" ;;
    *) echo "unsupported ABI $1" >&2; return 1 ;;
  esac
}

mkdir -p "${DEST}" "${HEADERS}"
for ABI_DIR in "${PREFIX_ROOT}"/*; do
  [[ -d "${ABI_DIR}" ]] || continue
  ABI="$(basename "${ABI_DIR}")"
  mkdir -p "${DEST}/${ABI}"
  if [[ -d "${ABI_DIR}/lib" ]]; then
    find "${ABI_DIR}/lib" -name "*.so" -exec cp -f {} "${DEST}/${ABI}/" \;
  fi
  if [[ -d "${ABI_DIR}/include" ]]; then
    cp -R "${ABI_DIR}/include/." "${HEADERS}/"
  fi
  if [[ -n "${NDK_HOME}" ]]; then
    TRIPLE="$(sysroot_triple "${ABI}")"
    CXX_SHARED="${NDK_HOME}/toolchains/llvm/prebuilt/$(host_tag)/sysroot/usr/lib/${TRIPLE}/libc++_shared.so"
    if [[ -f "${CXX_SHARED}" ]]; then
      cp -f "${CXX_SHARED}" "${DEST}/${ABI}/"
    else
      # FFmpeg links libass/HarfBuzz, so the C++ runtime must ship with it.
      echo "libc++_shared.so not found for ${ABI} at ${CXX_SHARED}" >&2
      exit 1
    fi
  fi
done
echo "Copied shared libraries into ${DEST}"
echo "Copied FFmpeg headers into ${HEADERS}"
```

