package com.burnsubtitle.domain.usecase

import android.net.Uri
import com.burnsubtitle.data.saf.SafDocumentQuery
import com.burnsubtitle.data.saf.SafUriPermissions
import com.burnsubtitle.domain.error.FileSelectionException
import com.burnsubtitle.domain.model.VideoSource
import com.burnsubtitle.domain.util.MediaFileNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SelectVideoUseCase @Inject constructor(
    private val documents: SafDocumentQuery,
    private val permissions: SafUriPermissions,
    private val probeVideo: ProbeVideoUseCase,
) {
    suspend operator fun invoke(uri: Uri, previousUri: String? = null): VideoSource {
        return withContext(Dispatchers.IO) {
            val info = documents.query(uri)
            if (!MediaFileNames.isVideoFile(info.displayName, info.mimeType)) {
                throw FileSelectionException.NotAVideo()
            }
            permissions.takePersistableRead(uri)
            val video = try {
                probeVideo(
                    uri = uri,
                    displayName = info.displayName,
                    sizeBytes = info.sizeBytes.coerceAtLeast(0L),
                    mimeHint = info.mimeType,
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
                if (error is FileSelectionException) throw error
                throw FileSelectionException.VideoUnreadable(error)
            }
            permissions.releaseIfDifferent(previousUri, uri)
            video
        }
    }
}
