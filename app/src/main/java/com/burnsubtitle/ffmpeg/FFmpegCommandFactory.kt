package com.burnsubtitle.ffmpeg

import com.burnsubtitle.domain.model.BurnJob
import javax.inject.Inject

class FFmpegCommandFactory @Inject constructor() {
    fun build(job: BurnJob): List<String> {
        val filter = buildSubtitlesFilter(job)
        return listOf(
            "-hide_banner",
            "-nostdin",
            "-y",
            "-loglevel",
            "info",
            "-stats_period",
            "0.2",
            "-i",
            job.videoCachePath,
            "-map",
            "0:v:0",
            "-map",
            "0:a?",
            "-vf",
            filter,
            "-c:v",
            VIDEO_CODEC,
            "-crf",
            CRF.toString(),
            "-preset",
            PRESET,
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "copy",
            "-sn",
            "-movflags",
            "+faststart",
            job.outputPath,
        )
    }

    fun buildSubtitlesFilter(job: BurnJob): String {
        val ass = escapeFilterPath(job.assPath)
        val fonts = escapeFilterPath(job.fontsDir)
        val width = job.videoWidth.coerceAtLeast(1)
        val height = job.videoHeight.coerceAtLeast(1)
        return "subtitles=filename=$ass:fontsdir=$fonts:charenc=UTF-8:original_size=${width}x$height"
    }

    companion object {
        const val VIDEO_CODEC = "libx264"
        const val CRF = 18
        const val PRESET = "veryfast"
        const val EXIT_CANCELLED = 255
        const val EXIT_NOT_LINKED = 64

        fun escapeFilterPath(path: String): String {
            return path
                .replace('\\', '/')
                .replace(":", "\\:")
                .replace("'", "\\'")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace(",", "\\,")
                .replace(";", "\\;")
        }

        fun describe(args: List<String>): String = args.joinToString(" ")
    }
}
