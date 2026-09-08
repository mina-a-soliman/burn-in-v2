package com.burnsubtitle.domain.session

import com.burnsubtitle.domain.model.BurnResult
import com.burnsubtitle.domain.model.SubtitleSource
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.model.VideoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BurnSession @Inject constructor() {
    private val _video = MutableStateFlow<VideoSource?>(null)
    val video: StateFlow<VideoSource?> = _video.asStateFlow()

    private val _subtitle = MutableStateFlow<SubtitleSource?>(null)
    val subtitle: StateFlow<SubtitleSource?> = _subtitle.asStateFlow()

    private val _style = MutableStateFlow(SubtitleStyle())
    val style: StateFlow<SubtitleStyle> = _style.asStateFlow()

    private val _jobId = MutableStateFlow<String?>(null)
    val jobId: StateFlow<String?> = _jobId.asStateFlow()

    private val _result = MutableStateFlow<BurnResult?>(null)
    val result: StateFlow<BurnResult?> = _result.asStateFlow()

    fun setVideo(source: VideoSource?) {
        _video.value = source
    }

    fun setSubtitle(source: SubtitleSource?) {
        _subtitle.value = source
    }

    fun setStyle(style: SubtitleStyle) {
        _style.value = style
    }

    fun setJobId(id: String?) {
        _jobId.value = id
    }

    fun setResult(result: BurnResult?) {
        _result.value = result
    }

    fun resetOutput() {
        _jobId.value = null
        _result.value = null
    }

    fun resetAll() {
        _video.value = null
        _subtitle.value = null
        _style.value = SubtitleStyle()
        resetOutput()
    }
}
