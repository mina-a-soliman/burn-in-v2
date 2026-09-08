package com.burnsubtitle.domain.parser

import com.burnsubtitle.domain.model.SubtitleDocument
import com.burnsubtitle.domain.model.SubtitleFormat

interface SubtitleParser {
    val format: SubtitleFormat
    fun parse(text: String): SubtitleDocument
}
