package com.burnsubtitle.domain.subtitle

object SubtitleMarkup {
    private val HTML_OR_VTT_TAG = Regex("""</?[^>]+>""")
    private val ASS_OVERRIDE = Regex("""\{.*?\}""")
    private val SRT_POSITION = Regex("""\{\\[an]?\d+\}""")

    fun strip(text: String): String {
        return SubtitleText.decodeHtmlEntities(
            text.replace(SRT_POSITION, "")
                .replace(ASS_OVERRIDE, "")
                .replace(HTML_OR_VTT_TAG, ""),
        ).trim()
    }
}
