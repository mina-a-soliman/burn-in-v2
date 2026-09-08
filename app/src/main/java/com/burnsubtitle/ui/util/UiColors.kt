package com.burnsubtitle.ui.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.burnsubtitle.R
import com.burnsubtitle.domain.model.SubtitlePosition

fun Long.toComposeColor(): Color = Color((this and 0xFFFFFFFFL).toInt())

fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

fun SubtitlePosition.labelRes(): Int = when (this) {
    SubtitlePosition.BOTTOM_LEFT -> R.string.pos_bottom_left
    SubtitlePosition.BOTTOM_CENTER -> R.string.pos_bottom_center
    SubtitlePosition.BOTTOM_RIGHT -> R.string.pos_bottom_right
    SubtitlePosition.MIDDLE_LEFT -> R.string.pos_middle_left
    SubtitlePosition.MIDDLE_CENTER -> R.string.pos_middle_center
    SubtitlePosition.MIDDLE_RIGHT -> R.string.pos_middle_right
    SubtitlePosition.TOP_LEFT -> R.string.pos_top_left
    SubtitlePosition.TOP_CENTER -> R.string.pos_top_center
    SubtitlePosition.TOP_RIGHT -> R.string.pos_top_right
}
