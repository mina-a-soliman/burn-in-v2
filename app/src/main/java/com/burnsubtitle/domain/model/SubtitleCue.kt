package com.burnsubtitle.domain.model

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
