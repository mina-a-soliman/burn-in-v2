package com.burnsubtitle.ui.home

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burnsubtitle.R
import com.burnsubtitle.ui.components.SelectionCard
import com.burnsubtitle.ui.picker.PersistableOpenDocument
import com.burnsubtitle.ui.picker.SafMimeTypes
import com.burnsubtitle.ui.util.formatDuration
import com.burnsubtitle.ui.util.formatFileSize
import com.burnsubtitle.ui.util.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val videoPicker = rememberLauncherForActivityResult(PersistableOpenDocument()) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri)
    }
    val subtitlePicker = rememberLauncherForActivityResult(PersistableOpenDocument()) { uri ->
        if (uri != null) viewModel.onSubtitlePicked(uri)
    }
    val outputFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.onOutputFolderPicked(uri)
    }
    val errorLogsFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.onErrorLogsFolderPicked(uri)
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(state.errorRes) {
        val errorRes = state.errorRes ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(errorRes))
        viewModel.clearError()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.home_subtitle_summary), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.home_formats), style = MaterialTheme.typography.bodyMedium)

            SelectionCard(
                title = stringResource(R.string.home_pick_video),
                body = when {
                    state.probingVideo -> stringResource(R.string.home_reading_video)
                    else -> state.video?.displayName ?: stringResource(R.string.home_video_none)
                },
                supporting = state.video?.takeIf { !state.probingVideo }?.let { video ->
                    buildString {
                        append(
                            context.getString(
                                R.string.home_video_meta,
                                video.width,
                                video.height,
                                formatDuration(video.durationMs),
                            ),
                        )
                        if (video.sizeBytes > 0L) {
                            append(" · ")
                            append(formatFileSize(context, video.sizeBytes))
                        }
                    }
                },
                icon = Icons.Filled.Movie,
                onClick = { videoPicker.launch(SafMimeTypes.VIDEO) },
            )
            SelectionCard(
                title = stringResource(R.string.home_pick_subtitle),
                body = when {
                    state.probingSubtitle -> stringResource(R.string.home_reading_subtitle)
                    else -> state.subtitle?.displayName ?: stringResource(R.string.home_subtitle_none)
                },
                supporting = state.subtitle?.takeIf { !state.probingSubtitle }?.let { subtitle ->
                    if (subtitle.cueCount > 0) {
                        stringResource(
                            R.string.home_subtitle_meta,
                            subtitle.format.name,
                            subtitle.cueCount,
                        )
                    } else {
                        subtitle.format.name
                    }
                },
                icon = Icons.Filled.Subtitles,
                onClick = { subtitlePicker.launch(SafMimeTypes.SUBTITLE) },
            )

            SelectionCard(
                title = stringResource(R.string.home_output_folder),
                body = state.outputFolderName ?: stringResource(R.string.home_output_folder_default),
                supporting = stringResource(R.string.home_output_folder_supporting),
                icon = Icons.Filled.Folder,
                onClick = { outputFolderPicker.launch(null) },
                onClear = if (state.hasCustomOutputFolder) {
                    { viewModel.clearOutputFolder() }
                } else null,
                clearContentDescription = stringResource(R.string.home_clear_folder),
            )

            SelectionCard(
                title = stringResource(R.string.home_error_logs_folder),
                body = state.errorLogsFolderName ?: stringResource(R.string.home_error_logs_folder_none),
                supporting = stringResource(R.string.home_error_logs_folder_supporting),
                icon = Icons.Filled.BugReport,
                onClick = { errorLogsFolderPicker.launch(null) },
                onClear = if (state.hasCustomErrorLogsFolder) {
                    { viewModel.clearErrorLogsFolder() }
                } else null,
                clearContentDescription = stringResource(R.string.home_clear_error_logs_folder),
            )

            Text(
                text = stringResource(
                    R.string.home_style_summary,
                    stringResource(state.style.position.labelRes()),
                    state.style.fontSize,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            FilledTonalButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_settings))
            }

            Button(
                onClick = { viewModel.export(onExport) },
                enabled = state.canExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                if (state.exporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_exporting))
                } else {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_export))
                }
            }
        }
    }
}
