package com.burnsubtitle.domain.usecase

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.burnsubtitle.domain.error.FileSelectionException
import com.burnsubtitle.domain.model.VideoSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ProbeVideoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    operator fun invoke(uri: Uri, displayName: String, sizeBytes: Long = 0L, mimeHint: String? = null): VideoSource {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
        } catch (error: RuntimeException) {
            retriever.release()
            throw FileSelectionException.VideoUnreadable(error)
        } catch (error: Exception) {
            retriever.release()
            throw FileSelectionException.VideoUnreadable(error)
        }
        return try {
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: mimeHint
                ?: "video/mp4"
            if (mime.startsWith("audio/") || mime.startsWith("image/")) {
                throw FileSelectionException.NotAVideo()
            }
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
            val rawSize = probeSize(uri) ?: Pair(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
            )
            if (rawSize.first <= 0 || rawSize.second <= 0) {
                throw FileSelectionException.VideoUnreadable()
            }
            val size = if (rotation == 90 || rotation == 270) {
                Pair(rawSize.second, rawSize.first)
            } else {
                rawSize
            }
            VideoSource(
                contentUri = uri.toString(),
                displayName = displayName,
                durationMs = duration,
                width = size.first,
                height = size.second,
                mimeType = mime,
                sizeBytes = sizeBytes,
            )
        } finally {
            retriever.release()
        }
    }

    private fun probeSize(uri: Uri): Pair<Int, Int>? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            if (width <= 0 || height <= 0) null else Pair(width, height)
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }
}
