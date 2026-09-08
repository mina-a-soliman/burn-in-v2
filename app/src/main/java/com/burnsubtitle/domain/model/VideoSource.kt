package com.burnsubtitle.domain.model

data class VideoSource(
    val contentUri: String,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val codecMimeType: String? = null,
) {
    val isAv1: Boolean
        get() = (codecMimeType?.contains("av01", ignoreCase = true) == true) ||
            (codecMimeType?.contains("av1", ignoreCase = true) == true) ||
            mimeType.contains("av01", ignoreCase = true) ||
            mimeType.contains("av1", ignoreCase = true)
}
