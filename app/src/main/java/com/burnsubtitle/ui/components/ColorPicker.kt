package com.burnsubtitle.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor
import com.burnsubtitle.R
import com.burnsubtitle.ui.util.toComposeColor

private val PresetColors = listOf(
    0xFFFFFFFFL,
    0xFFFFFF00L,
    0xFF00FFFFL,
    0xFFFFCC00L,
    0xFFFF3B30L,
    0xFF34C759L,
    0xFF007AFFL,
    0xFFFF2D55L,
    0xFF000000L,
)

@Composable
fun ArgbColorPicker(
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    includeAlpha: Boolean = false,
) {
    val hsv = remember { FloatArray(3) }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(1f) }
    var alpha by remember { mutableIntStateOf(255) }

    LaunchedEffect(value) {
        val argb = (value and 0xFFFFFFFFL).toInt()
        val current = AndroidColor.HSVToColor(alpha, floatArrayOf(hue, saturation, brightness))
        if (current != argb) {
            AndroidColor.colorToHSV(argb, hsv)
            hue = hsv[0]
            saturation = hsv[1]
            brightness = hsv[2]
            alpha = AndroidColor.alpha(argb)
        }
    }

    fun emit() {
        val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
        val nextAlpha = if (includeAlpha) alpha else 0xFF
        val packed = ((nextAlpha and 0xFF).toLong() shl 24) or (rgb.toLong() and 0x00FFFFFFL)
        onValueChange(packed)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetColors.forEach { preset ->
                    val selected = (preset and 0x00FFFFFFL) == (value and 0x00FFFFFFL)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(preset.toComposeColor())
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable {
                                val nextAlpha = if (includeAlpha) alpha else 0xFF
                                onValueChange((preset and 0x00FFFFFFL) or (nextAlpha.toLong() shl 24))
                            },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(value.toComposeColor())
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            )
        }
        ColorSlider(
            label = stringResource(R.string.style_hue),
            value = hue,
            valueRange = 0f..360f,
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                ),
            ),
            onValueChange = {
                hue = it
                emit()
            },
        )
        ColorSlider(
            label = stringResource(R.string.style_saturation),
            value = saturation,
            valueRange = 0f..1f,
            brush = Brush.horizontalGradient(
                listOf(Color.Gray, AndroidColor.HSVToColor(floatArrayOf(hue, 1f, brightness)).let { Color(it) }),
            ),
            onValueChange = {
                saturation = it
                emit()
            },
        )
        ColorSlider(
            label = stringResource(R.string.style_brightness),
            value = brightness,
            valueRange = 0f..1f,
            brush = Brush.horizontalGradient(
                listOf(Color.Black, AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f)).let { Color(it) }),
            ),
            onValueChange = {
                brightness = it
                emit()
            },
        )
        if (includeAlpha) {
            ColorSlider(
                label = stringResource(R.string.style_background_opacity),
                value = alpha.toFloat(),
                valueRange = 0f..255f,
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, value.toComposeColor().copy(alpha = 1f)),
                ),
                onValueChange = {
                    alpha = it.toInt()
                    emit()
                },
            )
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label },
        )
    }
}
