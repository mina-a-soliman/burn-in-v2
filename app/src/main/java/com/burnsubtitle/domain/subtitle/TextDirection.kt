package com.burnsubtitle.domain.subtitle

enum class ParagraphDirection {
    LTR,
    RTL,
}

object TextDirection {
    fun detect(text: String): ParagraphDirection {
        for (char in text) {
            when (ScriptClassifier.classifyChar(char)) {
                ScriptKind.ARABIC -> return ParagraphDirection.RTL
                ScriptKind.LATIN -> return ParagraphDirection.LTR
                ScriptKind.COMMON -> Unit
            }
        }
        return ParagraphDirection.LTR
    }

    fun isRtl(text: String): Boolean = detect(text) == ParagraphDirection.RTL
}
