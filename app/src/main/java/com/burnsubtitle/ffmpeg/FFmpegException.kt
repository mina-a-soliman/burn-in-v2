package com.burnsubtitle.ffmpeg

sealed class FFmpegException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotAvailable(cause: Throwable? = null) : FFmpegException("ffmpeg-missing", cause)
    class Cancelled : FFmpegException("ffmpeg-cancelled")
    class Failed(val exitCode: Int, val lastLog: String = "") : FFmpegException(
        if (lastLog.isNotBlank()) "ffmpeg-failed (exit $exitCode):\n$lastLog" else "ffmpeg-failed (exit $exitCode)",
    )
    class InvalidOutput : FFmpegException("ffmpeg-no-output")
}
