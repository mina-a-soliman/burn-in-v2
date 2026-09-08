package com.burnsubtitle.domain.model

data class BurnProgress(
    val fraction: Float,
    val timeMs: Long,
    val durationMs: Long,
    val logLine: String = "",
)
