package com.burnsubtitle.ffmpeg

enum class FFmpegFailureReason {
    UNSUPPORTED_ENCODING,
    FILTER_FAILURE,
    CODEC_FAILURE,
    MISSING_FONT,
    INSUFFICIENT_STORAGE,
    INVALID_VIDEO,
    INVALID_SUBTITLE,
    GENERAL,
}

sealed class FFmpegException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotAvailable(cause: Throwable? = null) : FFmpegException("ffmpeg-missing", cause)
    class Cancelled : FFmpegException("ffmpeg-cancelled")
    class Failed(
        val exitCode: Int,
        val lastLog: String = "",
        val reason: FFmpegFailureReason = diagnose(lastLog),
    ) : FFmpegException(
        if (lastLog.isNotBlank()) "ffmpeg-failed (exit $exitCode, reason $reason):\n$lastLog" else "ffmpeg-failed (exit $exitCode)",
    ) {
        companion object {
            fun diagnose(lastLog: String): FFmpegFailureReason {
                val lower = lastLog.lowercase()
                return when {
                    "character encoding" in lower || "iconv" in lower || "sub_charenc" in lower ->
                        FFmpegFailureReason.UNSUPPORTED_ENCODING
                    "font" in lower && ("not found" in lower || "missing" in lower || "cannot load" in lower || "no fonts" in lower) ->
                        FFmpegFailureReason.MISSING_FONT
                    "hardware accelerated" in lower || "failed to get pixel format" in lower ||
                        "mediacodec" in lower || "get current frame error" in lower ||
                        "encoder" in lower || "decoder" in lower || "codec" in lower || "libx264" in lower ->
                        FFmpegFailureReason.CODEC_FAILURE
                    "filtergraph" in lower || "filter" in lower || "libass" in lower ->
                        FFmpegFailureReason.FILTER_FAILURE
                    "no space left" in lower || "enospc" in lower ->
                        FFmpegFailureReason.INSUFFICIENT_STORAGE
                    "invalid data found" in lower || "moov atom not found" in lower || "stream not found" in lower ->
                        FFmpegFailureReason.INVALID_VIDEO
                    "invalid subtitle" in lower || "no readable cues" in lower ->
                        FFmpegFailureReason.INVALID_SUBTITLE
                    else -> FFmpegFailureReason.GENERAL
                }
            }
        }
    }
    class InvalidOutput : FFmpegException("ffmpeg-no-output")
}
