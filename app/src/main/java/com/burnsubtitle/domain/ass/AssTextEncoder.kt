package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.subtitle.BidiPreprocessor
import com.burnsubtitle.domain.subtitle.ScriptKind
import com.burnsubtitle.domain.subtitle.ScriptClassifier
import com.burnsubtitle.domain.subtitle.SubtitleFonts

object AssTextEncoder {
    fun encode(text: String): String {
        return text.split('\n').joinToString("\\N") { line ->
            encodeLine(line)
        }
    }

    fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val hours = total / 3_600_000
        val minutes = (total % 3_600_000) / 60_000
        val seconds = (total % 60_000) / 1000
        val centis = (total % 1000) / 10
        return "%d:%02d:%02d.%02d".format(java.util.Locale.US, hours, minutes, seconds, centis)
    }

    private fun encodeLine(line: String): String {
        val runs = ScriptClassifier.splitRuns(line)
        if (runs.isEmpty()) return ""
        val hasArabic = runs.any { it.kind == ScriptKind.ARABIC }
        val hasLatin = runs.any { it.kind == ScriptKind.LATIN }
        return buildString {
            runs.forEach { run ->
                val escaped = escape(run.text)
                val directed = when {
                    run.kind == ScriptKind.ARABIC && hasArabic -> {
                        "${BidiPreprocessor.RLI}$escaped${BidiPreprocessor.PDI}"
                    }
                    run.kind == ScriptKind.LATIN && hasArabic && hasLatin -> {
                        "${BidiPreprocessor.LRI}$escaped${BidiPreprocessor.PDI}"
                    }
                    else -> escaped
                }
                when (run.kind) {
                    ScriptKind.LATIN -> {
                        append("{\\fn")
                        append(SubtitleFonts.LATIN_FAMILY)
                        append('}')
                        append(directed)
                    }
                    ScriptKind.ARABIC -> {
                        append("{\\fn")
                        append(SubtitleFonts.ARABIC_FAMILY)
                        append('}')
                        append(directed)
                    }
                    ScriptKind.COMMON -> append(directed)
                }
            }
        }
    }

    private fun escape(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("{", "\\{")
            .replace("}", "\\}")
    }
}
