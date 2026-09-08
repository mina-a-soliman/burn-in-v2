package com.burnsubtitle.domain.model

data class SubtitleDocument(
    val cues: List<SubtitleCue>,
    val sourceFormat: SubtitleFormat,
)
