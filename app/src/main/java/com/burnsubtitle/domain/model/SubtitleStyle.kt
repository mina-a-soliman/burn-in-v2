package com.burnsubtitle.domain.model

data class SubtitleStyle(
    val position: SubtitlePosition = SubtitlePosition.BOTTOM_CENTER,
    val fontSize: Int = 42,
    val textColorArgb: Long = 0xFFFFFFFF,
    val backgroundEnabled: Boolean = true,
    val backgroundColorArgb: Long = 0x99000000,
    val outlineEnabled: Boolean = true,
    val outlineWidth: Float = 2.5f,
    val outlineColorArgb: Long = 0xFF000000,
    val shadowEnabled: Boolean = true,
    val shadowDepth: Float = 2.0f,
    val shadowColorArgb: Long = 0x80000000,
    val fontFamily: String = DEFAULT_FONT_FAMILY,
    val marginPercent: Int = 6,
) {
    companion object {
        const val DEFAULT_FONT_FAMILY = "Noto Naskh Arabic"
        const val MIN_FONT_SIZE = 18
        const val MAX_FONT_SIZE = 96
        const val MIN_MARGIN_PERCENT = 0
        const val MAX_MARGIN_PERCENT = 20
        const val MIN_OUTLINE_WIDTH = 0.5f
        const val MAX_OUTLINE_WIDTH = 8f
        const val MIN_SHADOW_DEPTH = 0.5f
        const val MAX_SHADOW_DEPTH = 8f
        const val MIN_BACKGROUND_ALPHA = 0
        const val MAX_BACKGROUND_ALPHA = 255
    }
}
