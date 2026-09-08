package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VttParserTest {
    @Test
    fun parsesMixedArabicAndEnglish() {
        val document = VttParser().parse(
            """
            WEBVTT

            00:00:01.000 --> 00:00:02.500
            Hello <b>مرحبا</b>
            """.trimIndent(),
        )
        assertEquals(SubtitleFormat.VTT, document.sourceFormat)
        assertEquals("Hello مرحبا", document.cues.single().text)
    }

    @Test
    fun parsesCueIdentifiersAndCueSettings() {
        val document = VttParser().parse(
            """
            WEBVTT

            NOTE this file has identifiers

            intro
            00:00:01.000 --> 00:00:02.500 align:start position:10%
            First

            02
            01:02:03.040 --> 01:02:04.000 line:90%
            Second
            """.trimIndent(),
        )
        assertEquals(2, document.cues.size)
        assertEquals(1000L, document.cues[0].startMs)
        assertEquals(2500L, document.cues[0].endMs)
        assertEquals("First", document.cues[0].text)
        assertEquals(3_723_040L, document.cues[1].startMs)
        assertEquals(3_724_000L, document.cues[1].endMs)
        assertEquals("Second", document.cues[1].text)
    }
}

class AssParserTest {
    @Test
    fun parsesDialogueWithArabic() {
        val document = AssParser().parse(
            """
            [Script Info]
            Title: Test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,مرحبا{\b1} world
            """.trimIndent(),
        )
        assertEquals(1, document.cues.size)
        assertTrue(document.cues.single().text.contains("مرحبا"))
        assertTrue(document.cues.single().text.contains("world"))
        assertEquals(1000L, document.cues.single().startMs)
    }
}
