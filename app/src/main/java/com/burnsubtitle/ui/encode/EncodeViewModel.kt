package com.burnsubtitle.ui.encode

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.burnsubtitle.data.work.BurnWorker
import com.burnsubtitle.domain.model.BurnResult
import com.burnsubtitle.domain.session.BurnSession
import com.burnsubtitle.domain.usecase.ResetSessionUseCase
import com.burnsubtitle.domain.usecase.StartBurnUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExportStatus {
    QUEUED,
    PREPARING,
    ENCODING,
    FINISHING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

data class EncodeUiState(
    val progress: Int = 0,
    val running: Boolean = true,
    val status: ExportStatus = ExportStatus.PREPARING,
    val videoName: String = "",
    val subtitleName: String = "",
    val result: BurnResult? = null,
)

@HiltViewModel
class EncodeViewModel @Inject constructor(
    private val session: BurnSession,
    private val workManager: WorkManager,
    private val startBurn: StartBurnUseCase,
    private val resetSession: ResetSessionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EncodeUiState(
            videoName = session.video.value?.displayName.orEmpty(),
            subtitleName = session.subtitle.value?.displayName.orEmpty(),
            running = session.jobId.value != null,
            status = if (session.jobId.value == null) ExportStatus.FAILED else ExportStatus.PREPARING,
            result = session.result.value,
        ),
    )
    val state: StateFlow<EncodeUiState> = _state.asStateFlow()

    init {
        val jobId = session.jobId.value
        if (jobId != null) {
            viewModelScope.launch {
                workManager.getWorkInfoByIdFlow(BurnWorker.parseId(jobId)).collect { info ->
                    if (info == null) return@collect
                    val progress = info.progress.getInt(BurnWorker.KEY_PROGRESS, 0)
                    val finished = info.state.isFinished
                    val result = if (finished) info.toBurnResult() else null
                    if (result != null) session.setResult(result)
                    _state.update {
                        it.copy(
                            progress = if (finished && info.state == WorkInfo.State.SUCCEEDED) 100 else progress,
                            running = !finished,
                            status = info.toStatus(progress),
                            result = result ?: it.result,
                        )
                    }
                }
            }
        } else if (session.result.value != null) {
            _state.update {
                it.copy(
                    running = false,
                    status = session.result.value.toStatus(),
                    result = session.result.value,
                    progress = if (session.result.value is BurnResult.Success) 100 else it.progress,
                )
            }
        }
    }

    fun cancel() {
        session.jobId.value?.let { startBurn.cancel(it) }
        // Stop showing progress right away; the work observer confirms the final state.
        _state.update { it.copy(status = ExportStatus.CANCELLED, running = false) }
    }

    fun startOver() {
        resetSession()
    }

    fun shareIntent(): Intent? = successIntent(Intent.ACTION_SEND)

    fun viewIntent(): Intent? = successIntent(Intent.ACTION_VIEW)

    private fun successIntent(action: String): Intent? {
        val success = _state.value.result as? BurnResult.Success ?: return null
        val uri = Uri.parse(success.outputUri)
        return Intent(action).apply {
            if (action == Intent.ACTION_SEND) {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                setDataAndType(uri, "video/mp4")
            }
            clipData = ClipData.newRawUri(success.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun WorkInfo.toBurnResult(): BurnResult {
        return when (state) {
            WorkInfo.State.SUCCEEDED -> BurnResult.Success(
                outputUri = outputData.getString(BurnWorker.KEY_OUTPUT_URI).orEmpty(),
                outputPath = outputData.getString(BurnWorker.KEY_OUTPUT_PATH).orEmpty(),
                displayName = outputData.getString(BurnWorker.KEY_DISPLAY_NAME).orEmpty(),
            )
            WorkInfo.State.CANCELLED -> BurnResult.Cancelled
            WorkInfo.State.FAILED -> if (outputData.getBoolean(BurnWorker.KEY_CANCELLED, false)) {
                BurnResult.Cancelled
            } else {
                BurnResult.Failure(
                    message = outputData.getString(BurnWorker.KEY_ERROR) ?: "Burn failed",
                    exitCode = outputData.getInt(BurnWorker.KEY_EXIT, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                )
            }
            else -> BurnResult.Failure(
                message = outputData.getString(BurnWorker.KEY_ERROR) ?: "Burn failed",
                exitCode = outputData.getInt(BurnWorker.KEY_EXIT, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            )
        }
    }

    private fun WorkInfo.toStatus(progress: Int): ExportStatus {
        return when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ExportStatus.QUEUED
            WorkInfo.State.RUNNING -> if (progress >= 95) ExportStatus.FINISHING else ExportStatus.ENCODING
            WorkInfo.State.SUCCEEDED -> ExportStatus.SUCCESS
            WorkInfo.State.CANCELLED -> ExportStatus.CANCELLED
            WorkInfo.State.FAILED -> if (outputData.getBoolean(BurnWorker.KEY_CANCELLED, false)) {
                ExportStatus.CANCELLED
            } else {
                ExportStatus.FAILED
            }
        }
    }

    private fun BurnResult?.toStatus(): ExportStatus = when (this) {
        is BurnResult.Success -> ExportStatus.SUCCESS
        is BurnResult.Failure -> ExportStatus.FAILED
        BurnResult.Cancelled -> ExportStatus.CANCELLED
        null -> ExportStatus.FAILED
    }
}
