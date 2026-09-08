package com.burnsubtitle.domain.error

sealed class FileSelectionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotAVideo(cause: Throwable? = null) : FileSelectionException("not-a-video", cause)
    class VideoUnreadable(cause: Throwable? = null) : FileSelectionException("video-unreadable", cause)
    class UnsupportedSubtitle(cause: Throwable? = null) : FileSelectionException("unsupported-subtitle", cause)
    class SubtitleUnreadable(cause: Throwable? = null) : FileSelectionException("subtitle-unreadable", cause)
    class SubtitleTooLarge(cause: Throwable? = null) : FileSelectionException("subtitle-too-large", cause)
    class SubtitleInvalid(cause: Throwable? = null) : FileSelectionException("subtitle-invalid", cause)
    class InsufficientSpace(cause: Throwable? = null) : FileSelectionException("insufficient-space", cause)
    class FontsMissing(cause: Throwable? = null) : FileSelectionException("fonts-missing", cause)
}
