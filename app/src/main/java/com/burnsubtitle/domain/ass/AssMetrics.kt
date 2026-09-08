package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleStyle
import kotlin.math.roundToInt

object AssMetrics {
    const val REFERENCE_PLAY_RES_Y = 1080

    fun fontSize(uiSize: Int, playResY: Int): Int {
        val size = uiSize.coerceIn(SubtitleStyle.MIN_FONT_SIZE, SubtitleStyle.MAX_FONT_SIZE)
        return scale(size.toDouble(), playResY).roundToInt().coerceIn(12, 300)
    }

    fun outline(width: Float, playResY: Int, enabled: Boolean): Float {
        if (!enabled) return 0f
        val clamped = width.coerceIn(SubtitleStyle.MIN_OUTLINE_WIDTH, SubtitleStyle.MAX_OUTLINE_WIDTH)
        return scale(clamped.toDouble(), playResY).toFloat().coerceAtLeast(0f)
    }

    fun shadow(depth: Float, playResY: Int, enabled: Boolean): Float {
        if (!enabled) return 0f
        val clamped = depth.coerceIn(SubtitleStyle.MIN_SHADOW_DEPTH, SubtitleStyle.MAX_SHADOW_DEPTH)
        return scale(clamped.toDouble(), playResY).toFloat().coerceAtLeast(0f)
    }

    fun margin(percent: Int, playResY: Int): Int {
        val clamped = percent.coerceIn(SubtitleStyle.MIN_MARGIN_PERCENT, SubtitleStyle.MAX_MARGIN_PERCENT)
        return ((playResY.coerceAtLeast(1) * clamped) / 100).coerceAtLeast(0)
    }

    private fun scale(value: Double, playResY: Int): Double {
        return value * playResY.coerceAtLeast(1) / REFERENCE_PLAY_RES_Y
    }
}
