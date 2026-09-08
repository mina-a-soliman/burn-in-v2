package com.burnsubtitle.domain.subtitle

enum class ScriptKind {
    ARABIC,
    LATIN,
    COMMON,
}

data class ScriptRun(
    val text: String,
    val kind: ScriptKind,
)

object ScriptClassifier {
    fun containsArabic(text: String): Boolean = text.any { classifyChar(it) == ScriptKind.ARABIC }

    fun containsLatin(text: String): Boolean = text.any { classifyChar(it) == ScriptKind.LATIN }

    fun classifyChar(char: Char): ScriptKind {
        val code = char.code
        return when {
            isArabic(code) -> ScriptKind.ARABIC
            isLatin(code) -> ScriptKind.LATIN
            else -> ScriptKind.COMMON
        }
    }

    fun splitRuns(text: String): List<ScriptRun> {
        if (text.isEmpty()) return emptyList()
        val raw = mutableListOf<ScriptRun>()
        val buffer = StringBuilder()
        var current = classifyChar(text[0]).let { if (it == ScriptKind.COMMON) ScriptKind.COMMON else it }
        text.forEach { char ->
            val kind = classifyChar(char)
            val assigned = if (kind == ScriptKind.COMMON) current else kind
            if (assigned != current && kind != ScriptKind.COMMON) {
                if (buffer.isNotEmpty()) {
                    raw += ScriptRun(buffer.toString(), current)
                    buffer.clear()
                }
                current = assigned
            }
            buffer.append(char)
            if (kind != ScriptKind.COMMON) current = kind
        }
        if (buffer.isNotEmpty()) raw += ScriptRun(buffer.toString(), current)
        return mergeCommon(raw)
    }

    fun isArabic(code: Int): Boolean {
        return code in 0x0600..0x06FF ||
            code in 0x0750..0x077F ||
            code in 0x08A0..0x08FF ||
            code in 0xFB50..0xFDFF ||
            code in 0xFE70..0xFEFF ||
            code in 0x1EE00..0x1EEFF
    }

    fun isLatin(code: Int): Boolean {
        return code in 0x0041..0x005A ||
            code in 0x0061..0x007A ||
            code in 0x00C0..0x024F ||
            code in 0x1E00..0x1EFF
    }

    private fun mergeCommon(runs: List<ScriptRun>): List<ScriptRun> {
        if (runs.isEmpty()) return runs
        val merged = mutableListOf<ScriptRun>()
        for (run in runs) {
            val last = merged.lastOrNull()
            if (last != null && (run.kind == ScriptKind.COMMON || last.kind == run.kind || last.kind == ScriptKind.COMMON)) {
                val kind = if (last.kind == ScriptKind.COMMON) run.kind else last.kind
                merged[merged.lastIndex] = ScriptRun(last.text + run.text, kind)
            } else {
                merged += run
            }
        }
        return merged
    }
}
