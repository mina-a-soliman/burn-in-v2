package com.burnsubtitle.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burnsubtitle.R
import com.burnsubtitle.data.logging.ErrorLogger
import com.burnsubtitle.data.pref.AppPreferences
import com.burnsubtitle.domain.model.SubtitleSource
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.model.VideoSource
import com.burnsubtitle.domain.session.BurnSession
import com.burnsubtitle.domain.usecase.PrepareBurnJobUseCase
import com.burnsubtitle.domain.usecase.SelectSubtitleUseCase
import com.burnsubtitle.domain.usecase.SelectVideoUseCase
import com.burnsubtitle.domain.usecase.StartBurnUseCase
import com.burnsubtitle.ui.util.toHomeErrorRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val video: VideoSource? = null,
    val subtitle: SubtitleSource? = null,
    val style: SubtitleStyle = SubtitleStyle(),
    val outputFolderName: String? = null,
    val errorLogsFolderName: String? = null,
    val hasCustomOutputFolder: Boolean = false,
    val hasCustomErrorLogsFolder: Boolean = false,
    val errorRes: Int? = null,
    val exporting: Boolean = false,
    val probingVideo: Boolean = false,
    val probingSubtitle: Boolean = false,
) {
    val canExport: Boolean
        get() = video != null && subtitle != null && !exporting && !probingVideo && !probingSubtitle
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: BurnSession,
    private val selectVideo: SelectVideoUseCase,
    private val selectSubtitle: SelectSubtitleUseCase,
    private val prepareJob: PrepareBurnJobUseCase,
    private val startBurn: StartBurnUseCase,
    private val preferences: AppPreferences,
    private val errorLogger: ErrorLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var videoPickJob: Job? = null
    private var subtitlePickJob: Job? = null

    init {
        viewModelScope.launch {
            session.video.collect { video ->
                _state.update { it.copy(video = video) }
            }
        }
        viewModelScope.launch {
            session.subtitle.collect { subtitle ->
                _state.update { it.copy(subtitle = subtitle) }
            }
        }
        viewModelScope.launch {
            session.style.collect { style ->
                _state.update { it.copy(style = style) }
            }
        }
        viewModelScope.launch {
            preferences.outputFolder.collect { folder ->
                _state.update {
                    it.copy(
                        outputFolderName = folder?.displayName,
                        hasCustomOutputFolder = folder != null,
                    )
                }
            }
        }
        viewModelScope.launch {
            preferences.errorLogsFolder.collect { folder ->
                _state.update {
                    it.copy(
                        errorLogsFolderName = folder?.displayName,
                        hasCustomErrorLogsFolder = folder != null,
                    )
                }
            }
        }
    }

    fun onOutputFolderPicked(uri: Uri) {
        preferences.setOutputFolder(uri)
    }

    fun clearOutputFolder() {
        preferences.setOutputFolder(null)
    }

    fun onErrorLogsFolderPicked(uri: Uri) {
        preferences.setErrorLogsFolder(uri)
    }

    fun clearErrorLogsFolder() {
        preferences.setErrorLogsFolder(null)
    }

    fun onVideoPicked(uri: Uri) {
        videoPickJob?.cancel()
        videoPickJob = viewModelScope.launch {
            _state.update { it.copy(probingVideo = true, errorRes = null) }
            try {
                val video = selectVideo(uri, session.video.value?.contentUri)
                session.setVideo(video)
                session.resetOutput()
                _state.update { it.copy(errorRes = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                errorLogger.log(error, tag = "Select Video", extraDetails = mapOf("uri" to uri.toString()))
                _state.update { it.copy(errorRes = error.toHomeErrorRes()) }
            } finally {
                _state.update { it.copy(probingVideo = false) }
            }
        }
    }

    fun onSubtitlePicked(uri: Uri) {
        subtitlePickJob?.cancel()
        subtitlePickJob = viewModelScope.launch {
            _state.update { it.copy(probingSubtitle = true, errorRes = null) }
            try {
                val subtitle = selectSubtitle(uri, session.subtitle.value?.contentUri)
                session.setSubtitle(subtitle)
                session.resetOutput()
                _state.update { it.copy(errorRes = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                errorLogger.log(error, tag = "Select Subtitle", extraDetails = mapOf("uri" to uri.toString()))
                _state.update { it.copy(errorRes = error.toHomeErrorRes()) }
            } finally {
                _state.update { it.copy(probingSubtitle = false) }
            }
        }
    }

    fun export(onStarted: () -> Unit) {
        val video = session.video.value
        val subtitle = session.subtitle.value
        if (video == null || subtitle == null) {
            _state.update { it.copy(errorRes = R.string.home_export_need_files) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(exporting = true, errorRes = null) }
            try {
                val customFolder = preferences.getOutputFolderUri()?.toString()
                val job = prepareJob(video, subtitle, session.style.value, customFolder)
                session.setJobId(startBurn(job))
                _state.update { it.copy(exporting = false) }
                onStarted()
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(exporting = false) }
                throw cancelled
            } catch (error: Throwable) {
                errorLogger.log(
                    error,
                    tag = "Export Preparation",
                    extraDetails = mapOf(
                        "video" to video.displayName,
                        "subtitle" to subtitle.displayName,
                    ),
                )
                _state.update {
                    it.copy(exporting = false, errorRes = error.toHomeErrorRes())
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorRes = null) }
    }
}
