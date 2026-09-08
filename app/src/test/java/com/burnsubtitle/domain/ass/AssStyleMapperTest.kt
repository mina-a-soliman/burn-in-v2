package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.parser.AssParser
import com.burnsubtitle.domain.parser.SrtParser
import com.burnsubtitle.domain.parser.VttParser
import com.burnsubtitle.domain.subtitle.BidiPreprocessor
import com.burnsubtitle.domain.subtitle.SubtitleEngine
import com.burnsubtitle.domain.subtitle.SubtitleFonts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssStyleMapperTest {
    private val writer = AssDocumentWriter(AssStyleGenerator())
    private val engine = SubtitleEngine(
        parsers = listOf(SrtParser(), VttParser(), AssParser()),
        writer = writer,
    )

    @Test
    fun writesUtf8AssWithRequestedStyle() {
        val document = SubtitleDocument(
            cues = listOf(
                SubtitleCue(1, 1000, 2000, "مرحبا\nHello"),
            ),
            sourceFormat = SubtitleFormat.SRT,
        )
        val ass = writer.write(
            document,
            SubtitleStyle(
                position = SubtitlePosition.TOP_CENTER,
                fontSize = 48,
                backgroundEnabled = true,
                outlineEnabled = true,
                shadowEnabled = true,
            ),
            playResX = 1920,
            playResY = 1080,
        )
        assertTrue(ass.startsWith("\uFEFF"))
        assertTrue(ass.contains("PlayResX: 1920"))
        assertTrue(ass.contains("PlayResY: 1080"))
        assertTrue(ass.contains("Kerning: yes"))
        assertTrue(ass.contains("Alignment, MarginL"))
        assertTrue(ass.contains(",8,"))
        assertTrue(ass.contains("مرحبا"))
        assertTrue(ass.contains("Hello"))
        assertTrue(ass.contains("\\N"))
        assertTrue(ass.contains(SubtitleFonts.ARABIC_FAMILY))
        assertTrue(ass.contains(SubtitleFonts.LATIN_FAMILY))
        assertTrue(ass.contains("BorderStyle"))
        assertTrue(ass.contains(BidiPreprocessor.RLI))
    }

    @Test
    fun engineBurnsMixedArabicAndEnglish() {
        val document = engine.parse(
            """
            1
            00:00:01,000 --> 00:00:02,000
            Hello <i>مرحبا</i>
            """.trimIndent(),
            SubtitleFormat.SRT,
        )
        assertEquals("Hello مرحبا", document.cues.single().text)
        val ass = engine.toAss(document, SubtitleStyle(), 1280, 720)
        assertTrue(ass.contains("{\\fn${SubtitleFonts.LATIN_FAMILY}}"))
        assertTrue(ass.contains("{\\fn${SubtitleFonts.ARABIC_FAMILY}}"))
        assertTrue(ass.contains("charenc").not())
        assertTrue(ass.contains("0:00:01.00,0:00:02.00"))
    }
}
