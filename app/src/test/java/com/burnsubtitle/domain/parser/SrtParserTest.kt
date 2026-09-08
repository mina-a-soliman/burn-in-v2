package com.burnsubtitle.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserTest {
    private val parser = SrtParser()

    @Test
    fun parsesEnglishAndArabicCues() {
        val document = parser.parse(
            """
            1
            00:00:01,000 --> 00:00:03,500
            Hello world

            2
            00:00:04,000 --> 00:00:06,000
            مرحبا بالعالم
            """.trimIndent(),
        )
        assertEquals(2, document.cues.size)
        assertEquals("Hello world", document.cues[0].text)
        assertEquals("مرحبا بالعالم", document.cues[1].text)
        assertEquals(1000L, document.cues[0].startMs)
        assertEquals(3500L, document.cues[0].endMs)
    }

    @Test
    fun parsesDotMilliseconds() {
        val document = parser.parse(
            """
            1
            00:00:00.250 --> 00:00:01.000
            Test
            """.trimIndent(),
        )
        assertEquals(250L, document.cues.first().startMs)
        assertTrue(document.cues.first().text.contains("Test"))
    }
}
