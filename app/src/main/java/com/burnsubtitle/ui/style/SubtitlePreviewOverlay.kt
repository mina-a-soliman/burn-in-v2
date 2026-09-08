package com.burnsubtitle.ui.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.ui.util.toComposeColor

@Composable
fun SubtitlePreviewOverlay(
    text: String,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val alignment = style.position.toAlignment()
        val textColor = style.textColorArgb.toComposeColor()
        val background = if (style.backgroundEnabled) {
            style.backgroundColorArgb.toComposeColor()
        } else {
            Color.Transparent
        }
        val shadow = if (style.shadowEnabled) {
            Shadow(
                color = style.shadowColorArgb.toComposeColor(),
                blurRadius = style.shadowDepth * 3f,
            )
        } else {
            null
        }
        val fontSize = (style.fontSize / 2).sp
        val textAlign = when (style.position) {
            SubtitlePosition.BOTTOM_LEFT, SubtitlePosition.MIDDLE_LEFT, SubtitlePosition.TOP_LEFT -> TextAlign.Left
            SubtitlePosition.BOTTOM_RIGHT, SubtitlePosition.MIDDLE_RIGHT, SubtitlePosition.TOP_RIGHT -> TextAlign.Right
            else -> TextAlign.Center
        }
        val textStyle = TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = textAlign,
            textDirection = TextDirection.Content,
        )
        Box(
            modifier = Modifier
                .align(alignment)
                .padding((style.marginPercent * 1.6f).dp)
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (style.outlineEnabled) {
                val outline = style.outlineColorArgb.toComposeColor()
                val radius = style.outlineWidth.dp
                val offsets = listOf(
                    -1 to 0, 1 to 0, 0 to -1, 0 to 1,
                    -1 to -1, -1 to 1, 1 to -1, 1 to 1,
                )
                offsets.forEach { (x, y) ->
                    Text(
                        text = text,
                        color = outline,
                        style = textStyle,
                        modifier = Modifier.offset(x = radius * x, y = radius * y),
                    )
                }
            }
            Text(
                text = text,
                color = textColor,
                style = textStyle.copy(shadow = shadow),
            )
        }
    }
}

private fun SubtitlePosition.toAlignment(): Alignment = when (this) {
    SubtitlePosition.BOTTOM_LEFT -> Alignment.BottomStart
    SubtitlePosition.BOTTOM_CENTER -> Alignment.BottomCenter
    SubtitlePosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    SubtitlePosition.MIDDLE_LEFT -> Alignment.CenterStart
    SubtitlePosition.MIDDLE_CENTER -> Alignment.Center
    SubtitlePosition.MIDDLE_RIGHT -> Alignment.CenterEnd
    SubtitlePosition.TOP_LEFT -> Alignment.TopStart
    SubtitlePosition.TOP_CENTER -> Alignment.TopCenter
    SubtitlePosition.TOP_RIGHT -> Alignment.TopEnd
}
