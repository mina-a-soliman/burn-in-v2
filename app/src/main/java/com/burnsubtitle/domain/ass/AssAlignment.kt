package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle

data class AssMargins(
    val left: Int,
    val right: Int,
    val vertical: Int,
)

object AssAlignment {
    fun fromPosition(position: SubtitlePosition): Int = position.assAlignment

    fun margins(position: SubtitlePosition, playResX: Int, playResY: Int, marginPercent: Int): AssMargins {
        val percent = marginPercent.coerceIn(
            SubtitleStyle.MIN_MARGIN_PERCENT,
            SubtitleStyle.MAX_MARGIN_PERCENT,
        )
        val vertical = ((playResY.coerceAtLeast(1) * percent) / 100)
        val horizontal = ((playResX.coerceAtLeast(1) * percent) / 100)
        val left = when (position) {
            SubtitlePosition.BOTTOM_LEFT, SubtitlePosition.MIDDLE_LEFT, SubtitlePosition.TOP_LEFT -> horizontal
            else -> horizontal
        }
        val right = when (position) {
            SubtitlePosition.BOTTOM_RIGHT, SubtitlePosition.MIDDLE_RIGHT, SubtitlePosition.TOP_RIGHT -> horizontal
            else -> horizontal
        }
        return AssMargins(left = left, right = right, vertical = vertical)
    }
}
