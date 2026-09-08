package com.burnsubtitle.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.burnsubtitle.R
import com.burnsubtitle.domain.model.BurnResult

@Composable
fun ResultContent(
    result: BurnResult?,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (val current = result) {
            is BurnResult.Success -> {
                Text(stringResource(R.string.encode_status_success), style = MaterialTheme.typography.headlineSmall)
                Text(current.displayName, style = MaterialTheme.typography.titleMedium)
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.result_open))
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.result_share))
                }
            }
            is BurnResult.Failure -> {
                Text(stringResource(R.string.encode_status_failed), style = MaterialTheme.typography.headlineSmall)
                Text(current.message, color = MaterialTheme.colorScheme.error)
                if (current.exitCode != null) {
                    Text(
                        text = "Exit code: ${current.exitCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!current.details.isNullOrBlank()) {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (expanded) R.string.error_ffmpeg_hide_details else R.string.error_ffmpeg_show_details,
                            ),
                        )
                    }
                    if (expanded) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                        ) {
                            Text(
                                text = current.details,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
            BurnResult.Cancelled -> {
                Text(stringResource(R.string.encode_status_cancelled), style = MaterialTheme.typography.headlineSmall)
            }
            null -> {
                Text(stringResource(R.string.result_failed), style = MaterialTheme.typography.headlineSmall)
            }
        }
        Button(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.result_home))
        }
    }
}
