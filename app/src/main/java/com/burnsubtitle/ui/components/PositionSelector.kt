package com.burnsubtitle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.ui.util.labelRes

@Composable
fun PositionSelector(
    selected: SubtitlePosition,
    onSelected: (SubtitlePosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf(SubtitlePosition.TOP_LEFT, SubtitlePosition.TOP_CENTER, SubtitlePosition.TOP_RIGHT),
        listOf(SubtitlePosition.MIDDLE_LEFT, SubtitlePosition.MIDDLE_CENTER, SubtitlePosition.MIDDLE_RIGHT),
        listOf(SubtitlePosition.BOTTOM_LEFT, SubtitlePosition.BOTTOM_CENTER, SubtitlePosition.BOTTOM_RIGHT),
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { position ->
                    val label = stringResource(position.labelRes())
                    FilledTonalIconToggleButton(
                        checked = selected == position,
                        onCheckedChange = { onSelected(position) },
                        modifier = Modifier
                            .size(52.dp)
                            .semantics { contentDescription = label },
                    ) {
                        Text(text = position.glyph(), fontSize = 18.sp)
                    }
                }
            }
        }
        Text(
            text = stringResource(selected.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun SubtitlePosition.glyph(): String = when (this) {
    SubtitlePosition.TOP_LEFT -> "↖"
    SubtitlePosition.TOP_CENTER -> "↑"
    SubtitlePosition.TOP_RIGHT -> "↗"
    SubtitlePosition.MIDDLE_LEFT -> "←"
    SubtitlePosition.MIDDLE_CENTER -> "•"
    SubtitlePosition.MIDDLE_RIGHT -> "→"
    SubtitlePosition.BOTTOM_LEFT -> "↙"
    SubtitlePosition.BOTTOM_CENTER -> "↓"
    SubtitlePosition.BOTTOM_RIGHT -> "↘"
}
