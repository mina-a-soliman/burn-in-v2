package com.burnsubtitle.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
