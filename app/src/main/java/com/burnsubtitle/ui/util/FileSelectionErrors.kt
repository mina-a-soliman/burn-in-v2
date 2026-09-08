package com.burnsubtitle.ui.util

import com.burnsubtitle.R
import com.burnsubtitle.domain.error.FileSelectionException

fun Throwable.toHomeErrorRes(): Int {
    return when (this) {
        is FileSelectionException.NotAVideo -> R.string.error_video_not_video
        is FileSelectionException.VideoUnreadable -> R.string.error_video_unreadable
        is FileSelectionException.UnsupportedSubtitle -> R.string.error_subtitle_unsupported
        is FileSelectionException.SubtitleUnreadable -> R.string.error_subtitle_unreadable
        is FileSelectionException.SubtitleTooLarge -> R.string.error_subtitle_too_large
        is FileSelectionException.SubtitleInvalid -> R.string.error_subtitle_invalid
        is FileSelectionException.InsufficientSpace -> R.string.error_insufficient_space
        is FileSelectionException.FontsMissing -> R.string.error_fonts_missing
        else -> R.string.error_generic
    }
}
