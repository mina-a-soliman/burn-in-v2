package com.burnsubtitle.ui.encode

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burnsubtitle.R
import com.burnsubtitle.ui.result.ResultContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncodeScreen(
    onHome: () -> Unit,
    viewModel: EncodeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    BackHandler(enabled = state.running) { viewModel.cancel() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.encode_title)) }) },
    ) { padding ->
        if (state.running) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.size(88.dp),
                    strokeWidth = 6.dp,
                )
                Text(statusText(state.status), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.encode_working), style = MaterialTheme.typography.bodyLarge)
                if (state.videoName.isNotBlank()) {
                    Text(state.videoName, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.subtitleName.isNotBlank()) {
                    Text(state.subtitleName, style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.encode_progress, state.progress),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(
                    onClick = viewModel::cancel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.encode_cancel))
                }
            }
        } else {
            ResultContent(
                result = state.result,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                onOpen = { viewModel.viewIntent()?.let(shareLauncher::launch) },
                onShare = {
                    viewModel.shareIntent()?.let { intent ->
                        shareLauncher.launch(Intent.createChooser(intent, null))
                    }
                },
                onHome = {
                    viewModel.startOver()
                    onHome()
                },
            )
        }
    }
}

@Composable
private fun statusText(status: ExportStatus): String = stringResource(
    when (status) {
        ExportStatus.QUEUED -> R.string.encode_status_queued
        ExportStatus.PREPARING -> R.string.encode_status_preparing
        ExportStatus.ENCODING -> R.string.encode_status_encoding
        ExportStatus.FINISHING -> R.string.encode_status_finishing
        ExportStatus.SUCCESS -> R.string.encode_status_success
        ExportStatus.FAILED -> R.string.encode_status_failed
        ExportStatus.CANCELLED -> R.string.encode_status_cancelled
    },
)
