package com.burnsubtitle.domain.model

data class BurnJob(
    val id: String,
    val videoCachePath: String,
    val assPath: String,
    val outputPath: String,
    val fontsDir: String,
    val style: SubtitleStyle,
    val videoWidth: Int,
    val videoHeight: Int,
    val durationMs: Long,
    val displayName: String,
    val outputFolderUri: String? = null,
)
