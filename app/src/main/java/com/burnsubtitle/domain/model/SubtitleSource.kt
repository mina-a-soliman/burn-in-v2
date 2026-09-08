package com.burnsubtitle.domain.model

data class SubtitleSource(
    val contentUri: String,
    val displayName: String,
    val format: SubtitleFormat,
    val sizeBytes: Long = 0L,
    val cueCount: Int = 0,
    val cachePath: String? = null,
)
