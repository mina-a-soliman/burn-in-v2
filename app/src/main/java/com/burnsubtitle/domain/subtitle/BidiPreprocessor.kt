package com.burnsubtitle.domain.subtitle

/**
 * Inserts Unicode bidi isolates so libass/FriBidi renders Arabic, English,
 * and mixed lines with the correct paragraph direction.
 *
 * Text stays in **logical** order. Visual reordering is left to FriBidi
 * inside FFmpeg's libass build.
 */
object BidiPreprocessor {
    const val LRI = '\u2066'
    const val RLI = '\u2067'
    const val FSI = '\u2068'
    const val PDI = '\u2069'

    fun prepareLine(text: String): String {
        if (text.isEmpty()) return text
        val hasArabic = ScriptClassifier.containsArabic(text)
        val hasLatin = ScriptClassifier.containsLatin(text)
        return when {
            hasArabic && hasLatin -> wrapMixed(text)
            hasArabic -> "$RLI$text$PDI"
            else -> text
        }
    }

    private fun wrapMixed(text: String): String {
        val encoded = StringBuilder()
        ScriptClassifier.splitRuns(text).forEach { run ->
            when (run.kind) {
                ScriptKind.ARABIC -> encoded.append(RLI).append(run.text).append(PDI)
                ScriptKind.LATIN -> encoded.append(LRI).append(run.text).append(PDI)
                ScriptKind.COMMON -> encoded.append(run.text)
            }
        }
        return encoded.toString()
    }
}
