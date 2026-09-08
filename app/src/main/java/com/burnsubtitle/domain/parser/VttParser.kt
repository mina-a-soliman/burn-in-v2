package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat

class VttParser : SubtitleParser {
    override val format: SubtitleFormat = SubtitleFormat.VTT

    override fun parse(text: String): SubtitleDocument {
        val normalized = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val body = normalized.substringAfter("WEBVTT", missingDelimiterValue = normalized)
        val blocks = body.split(BLOCK_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        val cues = blocks.mapIndexedNotNull { index, block -> parseBlock(block, index) }
        require(cues.isNotEmpty()) { "VTT file contains no cues" }
        return SubtitleDocument(cues = cues, sourceFormat = format)
    }

    private fun parseBlock(block: String, fallbackIndex: Int): SubtitleCue? {
        val lines = block.lines().filter { !it.startsWith("NOTE") && !it.startsWith("STYLE") }
        val timeLineIndex = lines.indexOfFirst { ARROW in it }
        if (timeLineIndex < 0) return null
        val times = lines[timeLineIndex].split(ARROW, limit = 2)
        if (times.size != 2) return null
        val start = parseTimestamp(times[0].trim()) ?: return null
        // "--> 00:00:02.500 align:start" splits with a leading space, so trim before
        // dropping the cue settings that follow the end timestamp.
        val end = parseTimestamp(times[1].trim().substringBefore(' ')) ?: return null
        val text = lines.drop(timeLineIndex + 1)
            .joinToString("\n")
            .replace(TAG, "")
            .trim()
        if (text.isEmpty()) return null
        return SubtitleCue(index = fallbackIndex + 1, startMs = start, endMs = end, text = text)
    }

    companion object {
        private const val ARROW = "-->"
        private val BLOCK_SPLIT = Regex("\n\n+")
        private val TAG = Regex("<[^>]+>")
        private val TIMESTAMP = Regex("""(?:(\d{1,3}):)?(\d{1,2}):(\d{2})\.(\d{1,3})""")

        fun parseTimestamp(value: String): Long? {
            val match = TIMESTAMP.find(value) ?: return SrtParser.parseTimestamp(value.replace('.', ','))
            val hours = match.groupValues[1].ifEmpty { "0" }.toLong()
            val minutes = match.groupValues[2].toLong()
            val seconds = match.groupValues[3].toLong()
            val fraction = match.groupValues[4].padEnd(3, '0').take(3).toLong()
            return (((hours * 60) + minutes) * 60 + seconds) * 1000 + fraction
        }
    }
}
