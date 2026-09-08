package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleFormatDetectorTest {
    @Test
    fun detectsSrtFromTimestamps() {
        val text = """
            1
            00:00:01,000 --> 00:00:03,000
            Hello
        """.trimIndent()
        assertEquals(SubtitleFormat.SRT, SubtitleFormatDetector.fromContent(text))
        assertEquals(SubtitleFormat.SRT, SubtitleFormatDetector.detect("notes.txt", "text/plain", text))
    }

    @Test
    fun detectsVttFromHeader() {
        val text = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            Hi
        """.trimIndent()
        assertEquals(SubtitleFormat.VTT, SubtitleFormatDetector.fromContent(text))
        assertEquals(SubtitleFormat.VTT, SubtitleFormatDetector.detect("clip.srt", null, text))
    }

    @Test
    fun detectsAssFromScriptInfo() {
        val text = """
            [Script Info]
            Title: Test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello
        """.trimIndent()
        assertEquals(SubtitleFormat.ASS, SubtitleFormatDetector.fromContent(text))
        assertEquals(SubtitleFormat.ASS, SubtitleFormatDetector.detect("movie.ssa", "text/plain", text))
    }

    @Test
    fun fallsBackToExtensionWhenContentIsUnknown() {
        assertEquals(
            SubtitleFormat.ASS,
            SubtitleFormatDetector.detect("style.ass", "application/octet-stream", "not a subtitle yet"),
        )
        assertEquals(
            SubtitleFormat.VTT,
            SubtitleFormatDetector.detect("captions.vtt", null, "NOTE this file is empty of cues"),
        )
    }

    @Test
    fun returnsNullForUnknownFiles() {
        assertNull(SubtitleFormatDetector.detect("readme.txt", "text/plain", "just some notes"))
        assertNull(SubtitleFormatDetector.fromContent(""))
    }
}
