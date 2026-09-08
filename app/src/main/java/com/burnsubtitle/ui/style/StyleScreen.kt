package com.burnsubtitle.ui.style

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burnsubtitle.R
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.ui.components.ArgbColorPicker
import com.burnsubtitle.ui.components.LabeledSlider
import com.burnsubtitle.ui.components.PositionSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleScreen(
    onBack: () -> Unit,
    viewModel: StyleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val style = state.style
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.style_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.style_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.style_preview), style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF101418)),
            ) {
                state.thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = state.videoName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SubtitlePreviewOverlay(
                    text = stringResource(R.string.style_preview_sample),
                    style = style,
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.style_position), style = MaterialTheme.typography.titleMedium)
            PositionSelector(
                selected = style.position,
                onSelected = viewModel::setPosition,
            )

            HorizontalDivider()
            LabeledSlider(
                label = stringResource(R.string.style_font_size),
                valueLabel = stringResource(R.string.style_font_size_value, style.fontSize),
                value = style.fontSize.toFloat(),
                valueRange = SubtitleStyle.MIN_FONT_SIZE.toFloat()..SubtitleStyle.MAX_FONT_SIZE.toFloat(),
                onValueChange = { viewModel.setFontSize(it.toInt()) },
            )

            HorizontalDivider()
            Text(stringResource(R.string.style_text_color), style = MaterialTheme.typography.titleMedium)
            ArgbColorPicker(
                value = style.textColorArgb,
                onValueChange = viewModel::setTextColor,
            )

            HorizontalDivider()
            SettingSwitch(
                label = stringResource(R.string.style_background),
                checked = style.backgroundEnabled,
                onCheckedChange = viewModel::setBackground,
            )
            if (style.backgroundEnabled) {
                Text(stringResource(R.string.style_background_color), style = MaterialTheme.typography.titleMedium)
                ArgbColorPicker(
                    value = style.backgroundColorArgb,
                    onValueChange = viewModel::setBackgroundColor,
                    includeAlpha = true,
                )
            }

            HorizontalDivider()
            SettingSwitch(
                label = stringResource(R.string.style_outline),
                checked = style.outlineEnabled,
                onCheckedChange = viewModel::setOutline,
            )
            if (style.outlineEnabled) {
                LabeledSlider(
                    label = stringResource(R.string.style_outline_width),
                    valueLabel = "%.1f".format(style.outlineWidth),
                    value = style.outlineWidth,
                    valueRange = SubtitleStyle.MIN_OUTLINE_WIDTH..SubtitleStyle.MAX_OUTLINE_WIDTH,
                    onValueChange = viewModel::setOutlineWidth,
                )
                Text(stringResource(R.string.style_outline_color), style = MaterialTheme.typography.titleMedium)
                ArgbColorPicker(
                    value = style.outlineColorArgb,
                    onValueChange = viewModel::setOutlineColor,
                )
            }

            HorizontalDivider()
            SettingSwitch(
                label = stringResource(R.string.style_shadow),
                checked = style.shadowEnabled,
                onCheckedChange = viewModel::setShadow,
            )
            if (style.shadowEnabled) {
                LabeledSlider(
                    label = stringResource(R.string.style_shadow_depth),
                    valueLabel = "%.1f".format(style.shadowDepth),
                    value = style.shadowDepth,
                    valueRange = SubtitleStyle.MIN_SHADOW_DEPTH..SubtitleStyle.MAX_SHADOW_DEPTH,
                    onValueChange = viewModel::setShadowDepth,
                )
                Text(stringResource(R.string.style_shadow_color), style = MaterialTheme.typography.titleMedium)
                ArgbColorPicker(
                    value = style.shadowColorArgb,
                    onValueChange = viewModel::setShadowColor,
                )
            }

            HorizontalDivider()
            LabeledSlider(
                label = stringResource(R.string.style_margin),
                valueLabel = "${style.marginPercent}%",
                value = style.marginPercent.toFloat(),
                valueRange = SubtitleStyle.MIN_MARGIN_PERCENT.toFloat()..SubtitleStyle.MAX_MARGIN_PERCENT.toFloat(),
                onValueChange = { viewModel.setMargin(it.toInt()) },
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
