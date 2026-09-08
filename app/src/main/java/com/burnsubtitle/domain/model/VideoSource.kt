package com.burnsubtitle.domain.model

data class VideoSource(
    val contentUri: String,
    val displayName: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val sizeBytes: Long = 0L,
)
