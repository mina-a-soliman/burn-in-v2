package com.burnsubtitle.ui.style

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.session.BurnSession
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StyleUiState(
    val style: SubtitleStyle = SubtitleStyle(),
    val previewText: String = "Hello مرحبا",
    val videoName: String? = null,
    val thumbnail: Bitmap? = null,
)

@HiltViewModel
class StyleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: BurnSession,
) : ViewModel() {

    private val _state = MutableStateFlow(StyleUiState(style = session.style.value))
    val state: StateFlow<StyleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.style.collect { style ->
                _state.update { it.copy(style = style) }
            }
        }
        viewModelScope.launch {
            session.video.collect { video ->
                _state.update { it.copy(videoName = video?.displayName) }
                if (video == null) {
                    _state.update { it.copy(thumbnail = null) }
                } else {
                    val frame = withContext(Dispatchers.IO) { thumbnail(video.contentUri) }
                    _state.update { it.copy(thumbnail = frame) }
                }
            }
        }
    }

    fun update(transform: (SubtitleStyle) -> SubtitleStyle) {
        session.setStyle(transform(_state.value.style))
    }

    fun setPosition(position: SubtitlePosition) = update { it.copy(position = position) }
    fun setFontSize(size: Int) = update { it.copy(fontSize = size) }
    fun setTextColor(color: Long) = update { it.copy(textColorArgb = color) }
    fun setBackground(enabled: Boolean) = update { it.copy(backgroundEnabled = enabled) }
    fun setBackgroundColor(color: Long) = update { it.copy(backgroundColorArgb = color) }
    fun setOutline(enabled: Boolean) = update { it.copy(outlineEnabled = enabled) }
    fun setOutlineWidth(width: Float) = update { it.copy(outlineWidth = width) }
    fun setOutlineColor(color: Long) = update { it.copy(outlineColorArgb = color) }
    fun setShadow(enabled: Boolean) = update { it.copy(shadowEnabled = enabled) }
    fun setShadowDepth(depth: Float) = update { it.copy(shadowDepth = depth) }
    fun setShadowColor(color: Long) = update { it.copy(shadowColorArgb = color) }
    fun setMargin(percent: Int) = update { it.copy(marginPercent = percent) }

    private fun thumbnail(uri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
