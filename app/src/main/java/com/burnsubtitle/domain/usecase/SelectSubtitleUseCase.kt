package com.burnsubtitle.domain.usecase

import android.net.Uri
import com.burnsubtitle.data.saf.SafDocumentQuery
import com.burnsubtitle.data.saf.SafFileCopier
import com.burnsubtitle.data.saf.SafUriPermissions
import com.burnsubtitle.data.saf.TempFileStore
import com.burnsubtitle.domain.error.FileSelectionException
import com.burnsubtitle.domain.model.SubtitleSource
import com.burnsubtitle.domain.parser.SubtitleFormatDetector
import com.burnsubtitle.domain.subtitle.SubtitleDecoder
import com.burnsubtitle.domain.subtitle.SubtitleEngine
import com.burnsubtitle.domain.subtitle.SubtitleText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SelectSubtitleUseCase @Inject constructor(
    private val documents: SafDocumentQuery,
    private val permissions: SafUriPermissions,
    private val copier: SafFileCopier,
    private val tempFiles: TempFileStore,
    private val engine: SubtitleEngine,
) {
    suspend operator fun invoke(uri: Uri, previousUri: String? = null): SubtitleSource {
        return withContext(Dispatchers.IO) {
            val info = documents.query(uri)
            if (info.sizeKnown && info.sizeBytes == 0L) {
                throw FileSelectionException.SubtitleInvalid()
            }
            if (info.sizeKnown && info.sizeBytes > TempFileStore.MAX_SUBTITLE_BYTES) {
                throw FileSelectionException.SubtitleTooLarge()
            }
            permissions.takePersistableRead(uri)
            try {
                val bytes = runCatching {
                    copier.readBytes(uri, TempFileStore.MAX_SUBTITLE_BYTES)
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    if (error is FileSelectionException) throw error
                    throw FileSelectionException.SubtitleUnreadable(error)
                }
                if (bytes.isEmpty()) {
                    throw FileSelectionException.SubtitleInvalid()
                }
                val text = SubtitleText.normalize(SubtitleDecoder.decode(bytes))
                val format = SubtitleFormatDetector.detect(info.displayName, info.mimeType, text)
                    ?: throw FileSelectionException.UnsupportedSubtitle()
                val document = runCatching { engine.parse(text, format) }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    throw FileSelectionException.SubtitleInvalid(error)
                }
                val cacheFile = tempFiles.writeSubtitlePick(
                    text.toByteArray(Charsets.UTF_8),
                    format.primaryExtension,
                )
                permissions.releaseIfDifferent(previousUri, uri)
                SubtitleSource(
                    contentUri = uri.toString(),
                    displayName = info.displayName,
                    format = format,
                    sizeBytes = if (info.sizeBytes > 0L) info.sizeBytes else bytes.size.toLong(),
                    cueCount = document.cues.size,
                    cachePath = cacheFile.absolutePath,
                )
            } catch (cancelled: CancellationException) {
                if (previousUri != uri.toString()) {
                    permissions.release(uri)
                }
                throw cancelled
            } catch (error: Throwable) {
                if (previousUri != uri.toString()) {
                    permissions.release(uri)
                }
                throw error
            }
        }
    }

}
