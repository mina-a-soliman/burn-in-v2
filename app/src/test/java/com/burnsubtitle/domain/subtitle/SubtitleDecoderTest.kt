package com.burnsubtitle.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SubtitleDecoderTest {

    @Test
    fun decodesUtf8WithBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "مرحبا".toByteArray(Charsets.UTF_8)
        assertEquals("مرحبا", SubtitleDecoder.decode(bytes))
    }

    @Test
    fun decodesUtf16WithBomAndDropsIt() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Hello مرحبا".toByteArray(Charsets.UTF_16LE)
        assertEquals("Hello مرحبا", SubtitleDecoder.decode(bytes))
    }

    @Test
    fun decodesPlainUtf8WithoutBom() {
        assertEquals(
            "Hello مرحبا",
            SubtitleDecoder.decode("Hello مرحبا".toByteArray(Charsets.UTF_8)),
        )
    }

    @Test
    fun decodesLegacyArabicWindows1256() {
        val text = "مرحبا بالعالم"
        val bytes = text.toByteArray(Charset.forName("windows-1256"))
        // The same bytes are not valid UTF-8, so a naive UTF-8 decode loses the text.
        assertTrue(String(bytes, Charsets.UTF_8).contains('\uFFFD'))
        assertEquals(text, SubtitleDecoder.decode(bytes))
    }

    @Test
    fun decodesBomlessUtf16Little() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nHello مرحبا\n"
        val bytes = text.toByteArray(Charsets.UTF_16LE)
        // These bytes are legal UTF-8, so a strict UTF-8 decode yields embedded NULs.
        assertTrue(String(bytes, Charsets.UTF_8).contains('\u0000'))
        assertEquals(text, SubtitleDecoder.decode(bytes))
    }

    @Test
    fun decodesBomlessUtf16Big() {
        val text = "1\n00:00:01,000 --> 00:00:02,000\nHello there\n"
        assertEquals(text, SubtitleDecoder.decode(text.toByteArray(Charsets.UTF_16BE)))
    }

    @Test
    fun keepsAsciiIntactForLegacyLatinBytes() {
        val bytes = "Caf\u00E9 time".toByteArray(Charset.forName("windows-1252"))
        assertEquals("Café time", SubtitleDecoder.decode(bytes))
    }
}
