package com.burnsubtitle.domain.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes subtitle bytes to text.
 *
 * Byte order marks win when present. Otherwise UTF-8 is attempted strictly, because a
 * strict decode only succeeds on genuine UTF-8. Files that fail are legacy single-byte
 * encodings; Arabic subtitles in the wild are overwhelmingly windows-1256, and Latin
 * ones windows-1252, so the fallback picks whichever produces more Arabic letters.
 */
object SubtitleDecoder {

    fun decode(bytes: ByteArray): String {
        bomCharset(bytes)?.let { (charset, offset) ->
            return String(bytes, offset, bytes.size - offset, charset)
        }
        // BOM-less UTF-16 is valid UTF-8 (NUL is a legal code point), so a strict UTF-8
        // decode would "succeed" and yield text riddled with NULs. Detect it first.
        bomlessUtf16Charset(bytes)?.let { charset -> return String(bytes, charset) }
        strictDecode(bytes, Charsets.UTF_8)?.let { return it }
        val arabic = legacyCharset(WINDOWS_1256)?.let { strictDecode(bytes, it) }
        val latin = legacyCharset(WINDOWS_1252)?.let { strictDecode(bytes, it) }
        return when {
            // Arabic words are contiguous letter runs. A lone Arabic letter is far more
            // likely to be an accented Latin character that windows-1256 happens to map.
            arabic != null && longestArabicRun(arabic) >= MIN_ARABIC_RUN -> arabic
            latin != null -> latin
            arabic != null -> arabic
            else -> String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun bomCharset(bytes: ByteArray): Pair<Charset, Int>? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return Charsets.UTF_8 to 3
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE to 2
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE to 2
        }
        return null
    }

    /**
     * Subtitle text is overwhelmingly ASCII-range (digits, arrows, timing punctuation), so
     * UTF-16 shows up as every other byte being NUL. Which half holds the NULs gives the
     * endianness.
     */
    private fun bomlessUtf16Charset(bytes: ByteArray): Charset? {
        val sampled = minOf(bytes.size, UTF16_SAMPLE_BYTES) and 1.inv()
        if (sampled < UTF16_MIN_BYTES) return null
        var evenNuls = 0
        var oddNuls = 0
        for (index in 0 until sampled) {
            if (bytes[index] == 0.toByte()) {
                if (index and 1 == 0) evenNuls++ else oddNuls++
            }
        }
        val pairs = sampled / 2
        val threshold = (pairs * UTF16_NUL_RATIO).toInt()
        return when {
            oddNuls >= threshold && evenNuls == 0 -> Charsets.UTF_16LE
            evenNuls >= threshold && oddNuls == 0 -> Charsets.UTF_16BE
            else -> null
        }
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset): String? {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun legacyCharset(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private fun longestArabicRun(text: String): Int {
        var longest = 0
        var current = 0
        text.forEach { char ->
            if (char in '\u0620'..'\u064A' || char in '\u0671'..'\u06D3') {
                current++
                if (current > longest) longest = current
            } else {
                current = 0
            }
        }
        return longest
    }

    private const val MIN_ARABIC_RUN = 2
    private const val UTF16_SAMPLE_BYTES = 4096
    private const val UTF16_MIN_BYTES = 16
    private const val UTF16_NUL_RATIO = 0.6
    private const val WINDOWS_1256 = "windows-1256"
    private const val WINDOWS_1252 = "windows-1252"
}
