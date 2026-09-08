package com.burnsubtitle.domain.model

enum class SubtitleFormat(val extensions: Set<String>, val mimeTypes: Set<String>) {
    SRT(
        extensions = setOf("srt"),
        mimeTypes = setOf(
            "application/x-subrip",
            "application/srt",
            "text/srt",
            "text/x-subrip",
        ),
    ),
    VTT(
        extensions = setOf("vtt"),
        mimeTypes = setOf(
            "text/vtt",
            "text/webvtt",
        ),
    ),
    ASS(
        extensions = setOf("ass", "ssa"),
        mimeTypes = setOf(
            "text/x-ssa",
            "application/x-ass",
            "text/x-ass",
            "application/x-ssa",
            "text/ssa",
        ),
    ),
    ;

    val primaryExtension: String get() = extensions.first()

    companion object {
        fun fromFileName(name: String): SubtitleFormat? {
            val extension = name.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
            return entries.firstOrNull { extension in it.extensions }
        }

        fun fromMimeType(mimeType: String?): SubtitleFormat? {
            val mime = mimeType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
            if (mime.isEmpty()) return null
            return entries.firstOrNull { mime in it.mimeTypes }
        }
    }
}
