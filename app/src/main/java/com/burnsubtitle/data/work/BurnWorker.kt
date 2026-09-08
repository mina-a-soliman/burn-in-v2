package com.burnsubtitle.data.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.burnsubtitle.R
import com.burnsubtitle.data.logging.ErrorLogger
import com.burnsubtitle.data.media.MediaStoreExporter
import com.burnsubtitle.data.saf.TempFileStore
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.ffmpeg.FFmpegEngine
import com.burnsubtitle.ffmpeg.FFmpegException
import com.burnsubtitle.ffmpeg.FFmpegFailureReason
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
import android.system.Os

@HiltWorker
class BurnWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: FFmpegEngine,
    private val processor: SubtitleBurnProcessor,
    private val exporter: MediaStoreExporter,
    private val notifier: BurnForegroundNotifier,
    private val tempFiles: TempFileStore,
    private val errorLogger: ErrorLogger,
) : CoroutineWorker(context, params) {

    private val stopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun doWork(): Result {
        engine.resetForNewJob()
        val job = jobFromInput()
        if (job == null) {
            val error = IllegalStateException("Missing burn job input data")
            errorLogger.log(error, tag = "BurnWorker")
            return Result.failure(workDataOf(KEY_ERROR to "Missing burn job"))
        }
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
                    val invalidOutput = FFmpegException.InvalidOutput()
                    errorLogger.log(
                        invalidOutput,
                        tag = "BurnWorker Output",
                        extraDetails = mapOf("jobId" to job.id, "outputPath" to job.outputPath),
                    )
                    return@coroutineScope Result.failure(workDataOf(KEY_ERROR to errorMessage(invalidOutput)))
                }
                val uri = withContext(Dispatchers.IO) {
                    val folderUri = job.outputFolderUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                    exporter.exportVideo(output, job.displayName, folderUri)
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
                val failed = error as? FFmpegException.Failed
                errorLogger.log(
                    error = error,
                    tag = "BurnWorker FFmpeg",
                    extraDetails = buildMap {
                        put("jobId", job.id)
                        put("videoName", job.displayName)
                        put("videoWidth", job.videoWidth)
                        put("videoHeight", job.videoHeight)
                        put("durationMs", job.durationMs)
                        put("videoCachePath", job.videoCachePath)
                        put("assPath", job.assPath)
                        put("outputPath", job.outputPath)
                        if (failed != null) {
                            put("exitCode", failed.exitCode)
                            put("failureReason", failed.reason.name)
                            if (failed.lastLog.isNotBlank()) {
                                put("ffmpegLogs", "\n" + failed.lastLog)
                            }
                        }
                    },
                )
                Result.failure(
                    workDataOf(
                        KEY_ERROR to errorMessage(error),
                        KEY_EXIT to (failed?.exitCode ?: Int.MIN_VALUE),
                        KEY_LOGS to (failed?.lastLog ?: ""),
                    ),
                )
            } catch (error: Exception) {
                errorLogger.log(
                    error = error,
                    tag = "BurnWorker Exception",
                    extraDetails = mapOf(
                        "jobId" to job.id,
                        "videoName" to job.displayName,
                        "videoWidth" to job.videoWidth,
                        "videoHeight" to job.videoHeight,
                        "durationMs" to job.durationMs,
                    ),
                )
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
            is FFmpegException.Failed -> when (error.reason) {
                FFmpegFailureReason.UNSUPPORTED_ENCODING -> R.string.error_ffmpeg_unsupported_encoding
                FFmpegFailureReason.FILTER_FAILURE -> R.string.error_ffmpeg_filter
                FFmpegFailureReason.CODEC_FAILURE -> R.string.error_ffmpeg_codec
                FFmpegFailureReason.MISSING_FONT -> R.string.error_fonts_missing
                FFmpegFailureReason.INSUFFICIENT_STORAGE -> R.string.error_insufficient_space
                FFmpegFailureReason.INVALID_VIDEO -> R.string.error_video_unreadable
                FFmpegFailureReason.INVALID_SUBTITLE -> R.string.error_subtitle_invalid
                FFmpegFailureReason.GENERAL -> R.string.error_ffmpeg_failed
            }
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
            outputFolderUri = inputData.getString(KEY_OUTPUT_FOLDER_URI),
            isAv1 = inputData.getBoolean(KEY_IS_AV1, false),
        )
    }
private fun setupFontconfig(context: Context, fontsDirPath: String) {
        val confDir = File(context.filesDir, "fontconfig")
        confDir.mkdirs()

        val confFile = File(confDir, "fonts.conf")
        
        // Write or overwrite the fonts.conf file to ensure it points to the correct directory
        confFile.writeText("""
            <?xml version="1.0"?>
            <!DOCTYPE fontconfig SYSTEM "fonts.dtd">
            <fontconfig>
                <dir>$fontsDirPath</dir>
                <cachedir>${context.cacheDir.absolutePath}</cachedir>
                <config></config>
            </fontconfig>
        """.trimIndent())

        // Set the environment variables for the native FFmpeg C++ code to read
        android.system.Os.setenv("FONTCONFIG_PATH", confDir.absolutePath, true)
        android.system.Os.setenv("FONTCONFIG_FILE", confFile.absolutePath, true)
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
        const val KEY_LOGS = "logs"
        const val KEY_EXIT = "exit"
        const val KEY_CANCELLED = "cancelled"
        const val KEY_OUTPUT_FOLDER_URI = "outputFolderUri"
        const val KEY_IS_AV1 = "isAv1"

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
            KEY_OUTPUT_FOLDER_URI to job.outputFolderUri,
            KEY_IS_AV1 to job.isAv1,
        )

        fun parseId(raw: String): UUID = UUID.fromString(raw)
    }
}
