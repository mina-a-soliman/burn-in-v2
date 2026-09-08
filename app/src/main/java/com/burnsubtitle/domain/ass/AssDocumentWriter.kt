package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleCue
import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssDocumentWriter @Inject constructor(
    private val styleGenerator: AssStyleGenerator,
) {
    fun write(
        document: SubtitleDocument,
        style: SubtitleStyle,
        playResX: Int,
        playResY: Int,
    ): String {
        val width = playResX.coerceAtLeast(1)
        val height = playResY.coerceAtLeast(1)
        val styleLine = styleGenerator.toStyleLine(style, width, height)
        // libass matches section headers and field names at column 0, so every line is
        // emitted unindented. A raw string with trimIndent() cannot be used here: the
        // interpolated dialogue block is multi-line, which makes the common indent 0 and
        // leaves the surrounding literal indented.
        val lines = buildList {
            add("[Script Info]")
            add("Title: Burn Subtitle")
            add("ScriptType: v4.00+")
            add("WrapStyle: 0")
            add("ScaledBorderAndShadow: yes")
            add("Kerning: yes")
            add("YCbCr Matrix: TV.709")
            add("PlayResX: $width")
            add("PlayResY: $height")
            add("LayoutResX: $width")
            add("LayoutResY: $height")
            add("")
            add("[V4+ Styles]")
            add(STYLE_FORMAT)
            add(styleLine)
            add("")
            add("[Events]")
            add(EVENT_FORMAT)
            document.cues.forEach { cue -> add(dialogueLine(cue)) }
        }
        return "\uFEFF" + lines.joinToString("\n") + "\n"
    }

    private fun dialogueLine(cue: SubtitleCue): String {
        return "Dialogue: 0," +
            AssTextEncoder.formatTime(cue.startMs) + "," +
            AssTextEncoder.formatTime(cue.endMs) +
            ",Default,,0,0,0,," +
            AssTextEncoder.encode(cue.text)
    }

    private companion object {
        const val STYLE_FORMAT = "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, " +
            "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, " +
            "Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding"
        const val EVENT_FORMAT =
            "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
    }
}
