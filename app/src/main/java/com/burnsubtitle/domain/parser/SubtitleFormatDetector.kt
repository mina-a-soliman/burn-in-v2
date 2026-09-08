package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleFormat

object SubtitleFormatDetector {
    private val SRT_TIMESTAMP = Regex(
        """\d{1,3}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,3}:\d{2}:\d{2}[,.]\d{1,3}""",
    )

    fun detect(displayName: String, mimeType: String?, text: String): SubtitleFormat? {
        return fromContent(text)
            ?: SubtitleFormat.fromFileName(displayName)
            ?: SubtitleFormat.fromMimeType(mimeType)
    }

    fun fromContent(text: String): SubtitleFormat? {
        val body = text.removePrefix("\uFEFF").trimStart()
        if (body.isEmpty()) return null
        if (body.startsWith("WEBVTT", ignoreCase = true)) return SubtitleFormat.VTT
        if (looksLikeAss(body)) return SubtitleFormat.ASS
        if (SRT_TIMESTAMP.containsMatchIn(body)) return SubtitleFormat.SRT
        return null
    }

    private fun looksLikeAss(body: String): Boolean {
        if (body.startsWith("[Script Info]", ignoreCase = true)) return true
        val hasEvents = body.contains("[Events]", ignoreCase = true)
        val hasDialogue = body.contains("\nDialogue:", ignoreCase = true) ||
            body.startsWith("Dialogue:", ignoreCase = true)
        return hasEvents && hasDialogue
    }
}
