package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat

class AssParser : SubtitleParser {
    override val format: SubtitleFormat = SubtitleFormat.ASS

    override fun parse(text: String): SubtitleDocument {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val eventFormat = normalized.lineSequence()
            .dropWhile { !it.trim().equals("[Events]", ignoreCase = true) }
            .drop(1)
            .firstOrNull { it.startsWith("Format:", ignoreCase = true) }
            ?.removePrefix("Format:")
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?: listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")

        val startIndex = eventFormat.indexOf("start").takeIf { it >= 0 } ?: 1
        val endIndex = eventFormat.indexOf("end").takeIf { it >= 0 } ?: 2
        val textIndex = eventFormat.indexOf("text").takeIf { it >= 0 } ?: (eventFormat.lastIndex)

        val cues = normalized.lineSequence()
            .filter { it.startsWith("Dialogue:", ignoreCase = true) }
            .mapIndexedNotNull { index, line ->
                val payload = line.substringAfter(':')
                val parts = splitKeepText(payload, textIndex)
                if (parts.size <= maxOf(startIndex, endIndex, textIndex)) return@mapIndexedNotNull null
                val start = parseAssTime(parts[startIndex]) ?: return@mapIndexedNotNull null
                val end = parseAssTime(parts[endIndex]) ?: return@mapIndexedNotNull null
                val cueText = parts[textIndex]
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace(OVERRIDE, "")
                    .trim()
                if (cueText.isEmpty()) return@mapIndexedNotNull null
                SubtitleCue(index = index + 1, startMs = start, endMs = end, text = cueText)
            }
            .toList()
        require(cues.isNotEmpty()) { "ASS file contains no dialogue cues" }
        return SubtitleDocument(cues = cues, sourceFormat = format)
    }

    private fun splitKeepText(payload: String, textIndex: Int): List<String> {
        val parts = mutableListOf<String>()
        var remaining = payload.trim()
        repeat(textIndex) {
            val comma = remaining.indexOf(',')
            if (comma < 0) return parts
            parts += remaining.substring(0, comma).trim()
            remaining = remaining.substring(comma + 1)
        }
        parts += remaining.trim()
        return parts
    }

    companion object {
        private val OVERRIDE = Regex("""\{.*?\}""")

        fun parseAssTime(value: String): Long? {
            val match = TIME.find(value.trim()) ?: return null
            val hours = match.groupValues[1].toLong()
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            val centis = match.groupValues[4].padEnd(2, '0').take(2).toLong()
            return (((hours * 60) + minutes) * 60 + seconds) * 1000 + centis * 10
        }

        private val TIME = Regex("""(\d+):(\d{2}):(\d{2})[.](\d{1,2})""")
    }
}
