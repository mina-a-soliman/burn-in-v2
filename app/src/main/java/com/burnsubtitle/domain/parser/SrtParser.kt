package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat

class SrtParser : SubtitleParser {
    override val format: SubtitleFormat = SubtitleFormat.SRT

    override fun parse(text: String): SubtitleDocument {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = normalized.split(BLOCK_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        val cues = blocks.mapIndexedNotNull { index, block -> parseBlock(block, index) }
        require(cues.isNotEmpty()) { "SRT file contains no cues" }
        return SubtitleDocument(cues = cues, sourceFormat = format)
    }

    private fun parseBlock(block: String, fallbackIndex: Int): SubtitleCue? {
        val lines = block.lines()
        if (lines.size < 2) return null
        val timeLineIndex = lines.indexOfFirst { ARROW in it }
        if (timeLineIndex < 0) return null
        val times = lines[timeLineIndex].split(ARROW, limit = 2)
        if (times.size != 2) return null
        val start = parseTimestamp(times[0].trim()) ?: return null
        val end = parseTimestamp(times[1].trim().substringBefore(' ')) ?: return null
        val text = lines.drop(timeLineIndex + 1).joinToString("\n").trim()
        if (text.isEmpty()) return null
        val index = lines.first().toIntOrNull() ?: (fallbackIndex + 1)
        return SubtitleCue(index = index, startMs = start, endMs = end, text = text)
    }

    companion object {
        private const val ARROW = "-->"
        private val BLOCK_SPLIT = Regex("\n\n+")
        private val TIMESTAMP = Regex("""(\d{1,3}):(\d{2}):(\d{2})[,.](\d{1,3})""")

        fun parseTimestamp(value: String): Long? {
            val match = TIMESTAMP.find(value) ?: return null
            val hours = match.groupValues[1].toLong()
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            val fraction = match.groupValues[4].padEnd(3, '0').take(3).toLong()
            return (((hours * 60) + minutes) * 60 + seconds) * 1000 + fraction
        }
    }
}
