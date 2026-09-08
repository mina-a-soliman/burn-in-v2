package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssConversionTest {
    @Test
    fun writesUnindentedSectionsForEveryCueCount() {
        val cues = (1..3).map { index ->
            SubtitleCue(
                index = index,
                startMs = index * 1000L,
                endMs = index * 1000L + 900L,
                text = "line $index\nمرحبا",
            )
        }
        val document = SubtitleDocument(cues = cues, sourceFormat = SubtitleFormat.SRT)
        val ass = AssDocumentWriter(AssStyleGenerator())
            .write(document, SubtitleStyle(), playResX = 1920, playResY = 1080)

        val body = ass.removePrefix("\uFEFF")
        // libass only recognises section headers and field names at column 0.
        body.lines().forEach { line ->
            assertTrue("indented ASS line: '$line'", line == line.trimStart())
        }
        assertTrue(body.startsWith("[Script Info]"))
        assertTrue(body.lines().contains("[V4+ Styles]"))
        assertTrue(body.lines().contains("[Events]"))
        assertEquals(3, body.lines().count { it.startsWith("Dialogue: ") })
        assertEquals(1, body.lines().count { it.startsWith("Style: Default,") })
    }

    @Test
    fun colorRoundTripsArgbToAss() {
        val white = 0xFFFFFFFFL
        val boxed = 0x99000000L
        assertEquals("&H00FFFFFF", AssColor.fromArgb(white))
        assertEquals(white, AssColor.toArgb(AssColor.fromArgb(white)))
        assertEquals(boxed, AssColor.toArgb(AssColor.fromArgb(boxed)))
    }

    @Test
    fun fontSizeScalesFrom1080pReference() {
        assertEquals(42, AssMetrics.fontSize(42, 1080))
        assertEquals(28, AssMetrics.fontSize(42, 720))
        assertEquals(84, AssMetrics.fontSize(42, 2160))
    }

    @Test
    fun positionMapsToNumpadAlignment() {
        assertEquals(2, AssAlignment.fromPosition(SubtitlePosition.BOTTOM_CENTER))
        assertEquals(7, AssAlignment.fromPosition(SubtitlePosition.TOP_LEFT))
        val margins = AssAlignment.margins(SubtitlePosition.BOTTOM_CENTER, 1920, 1080, 10)
        assertEquals(192, margins.left)
        assertEquals(108, margins.vertical)
    }
}
