package com.burnsubtitle.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSelectionValidationTest {
    private val parsers = listOf(SrtParser(), VttParser(), AssParser())

    @Test
    fun srtAssAndVttSamplesParseAfterDetection() {
        val samples = listOf(
            "movie.srt" to """
                1
                00:00:01,000 --> 00:00:02,500
                Hello
                مرحبا
            """.trimIndent(),
            "clip.vtt" to """
                WEBVTT

                00:00:00.000 --> 00:00:01.000
                Caption
            """.trimIndent(),
            "show.ass" to """
                [Script Info]
                Title: Test

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello\Nمرحبا
            """.trimIndent(),
        )
        samples.forEach { (name, text) ->
            val format = SubtitleFormatDetector.detect(name, null, text)
            val parser = parsers.first { it.format == format }
            val document = parser.parse(text)
            assertEquals(format, document.sourceFormat)
            assertTrue(name, document.cues.isNotEmpty())
        }
    }
}
