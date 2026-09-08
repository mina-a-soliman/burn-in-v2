package com.burnsubtitle.domain.subtitle

import java.text.Normalizer

object SubtitleText {
    fun normalize(raw: String): String {
        val withoutBom = raw.removePrefix("\uFEFF")
        val nfc = Normalizer.normalize(withoutBom, Normalizer.Form.NFC)
        return nfc.replace("\r\n", "\n").replace('\r', '\n')
    }

    fun decodeHtmlEntities(text: String): String {
        return ENTITY.replace(text) { match ->
            val named = match.groupValues[1]
            val decimal = match.groupValues[2]
            val hex = match.groupValues[3]
            when {
                named.isNotEmpty() -> NAMED[named] ?: match.value
                decimal.isNotEmpty() -> decimal.toIntOrNull()?.toChar()?.toString() ?: match.value
                hex.isNotEmpty() -> hex.toIntOrNull(16)?.toChar()?.toString() ?: match.value
                else -> match.value
            }
        }
    }

    private val ENTITY = Regex("""&(?:([a-zA-Z]+)|#(\d+)|#[xX]([0-9a-fA-F]+));""")
    private val NAMED = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
    )
}
