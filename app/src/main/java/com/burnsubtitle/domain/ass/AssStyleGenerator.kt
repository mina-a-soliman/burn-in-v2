package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.subtitle.SubtitleFonts
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssStyleGenerator @Inject constructor() {
    fun toStyleLine(style: SubtitleStyle, playResX: Int, playResY: Int): String {
        val fontSize = AssMetrics.fontSize(style.fontSize, playResY)
        val outline = AssMetrics.outline(style.outlineWidth, playResY, style.outlineEnabled)
        val shadow = AssMetrics.shadow(style.shadowDepth, playResY, style.shadowEnabled)
        val borderStyle = if (style.backgroundEnabled) 3 else 1
        val margins = AssAlignment.margins(style.position, playResX, playResY, style.marginPercent)
        val backColour = if (style.backgroundEnabled) {
            AssColor.fromArgb(style.backgroundColorArgb)
        } else {
            AssColor.fromArgb(style.shadowColorArgb)
        }
        // Style lines are comma separated, so a comma in the family name would shift
        // every field after it.
        val fontName = style.fontFamily
            .replace(',', ' ')
            .trim()
            .ifBlank { SubtitleFonts.ARABIC_FAMILY }
        return buildString {
            append("Style: Default,")
            append(fontName)
            append(',')
            append(fontSize)
            append(',')
            append(AssColor.fromArgb(style.textColorArgb))
            append(',')
            append(AssColor.fromArgb(style.textColorArgb))
            append(',')
            append(AssColor.fromArgb(style.outlineColorArgb))
            append(',')
            append(backColour)
            append(",0,0,0,0,100,100,0,0,")
            append(borderStyle)
            append(',')
            append("%.2f".format(Locale.US, outline))
            append(',')
            append("%.2f".format(Locale.US, shadow))
            append(',')
            append(AssAlignment.fromPosition(style.position))
            append(',')
            append(margins.left)
            append(',')
            append(margins.right)
            append(',')
            append(margins.vertical)
            append(",1")
        }
    }
}
