package com.burnsubtitle.domain.subtitle

import com.burnsubtitle.domain.ass.AssDocumentWriter
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.parser.SubtitleFormatDetector
import com.burnsubtitle.domain.parser.SubtitleParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleEngine @Inject constructor(
    // Without @JvmSuppressWildcards the injection site asks Dagger for
    // List<? extends SubtitleParser>, which no module provides.
    private val parsers: List<@JvmSuppressWildcards SubtitleParser>,
    private val writer: AssDocumentWriter,
) {
    fun parse(text: String, format: SubtitleFormat): SubtitleDocument {
        val parser = parsers.firstOrNull { it.format == format }
            ?: throw IllegalArgumentException("Unsupported subtitle format $format")
        val document = parser.parse(SubtitleText.normalize(text))
        val cues = document.cues.map { cue ->
            cue.copy(text = SubtitleMarkup.strip(cue.text))
        }.filter { it.text.isNotBlank() }
        require(cues.isNotEmpty()) { "Subtitle file contains no readable cues" }
        return document.copy(cues = cues)
    }

    fun parse(text: String, fileName: String, mimeType: String? = null): SubtitleDocument {
        val normalized = SubtitleText.normalize(text)
        val format = SubtitleFormatDetector.detect(fileName, mimeType, normalized)
            ?: throw IllegalArgumentException("Unsupported subtitle format")
        return parse(normalized, format)
    }

    fun toAss(
        document: SubtitleDocument,
        style: SubtitleStyle,
        playResX: Int,
        playResY: Int,
    ): String {
        return writer.write(document, style, playResX, playResY)
    }
}
