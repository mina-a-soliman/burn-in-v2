package com.burnsubtitle.data.saf

import android.content.Context
import com.burnsubtitle.domain.error.FileSelectionException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TempFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val picksDir: File get() = File(context.cacheDir, PICKS_DIR)
    private val jobsDir: File get() = File(context.cacheDir, JOBS_DIR)

    fun writeSubtitlePick(bytes: ByteArray, extension: String): File {
        val dir = picksDir.apply { mkdirs() }
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(SUBTITLE_PREFIX)) {
                file.delete()
            }
        }
        val cleanExtension = extension.trimStart('.').ifBlank { "txt" }
        val file = File(dir, "$SUBTITLE_PREFIX.$cleanExtension")
        file.writeBytes(bytes)
        return file
    }

    fun createJobDir(jobId: String): File {
        return File(jobsDir, jobId).apply { mkdirs() }
    }

    fun deleteJobDir(jobId: String) {
        File(jobsDir, jobId).deleteRecursively()
    }

    fun clearPicks() {
        picksDir.deleteRecursively()
    }

    fun usableSpaceBytes(): Long = context.cacheDir.usableSpace

    fun requireSpaceForVideo(sizeBytes: Long) {
        val copyAndOutput = if (sizeBytes > 0L) {
            sizeBytes.saturatingTimes(2) + HEADROOM_BYTES
        } else {
            UNKNOWN_VIDEO_HEADROOM_BYTES
        }
        if (usableSpaceBytes() < copyAndOutput) {
            throw FileSelectionException.InsufficientSpace()
        }
    }

    private fun Long.saturatingTimes(factor: Long): Long {
        return if (this > Long.MAX_VALUE / factor) Long.MAX_VALUE else this * factor
    }

    companion object {
        const val PICKS_DIR = "picks"
        const val JOBS_DIR = "jobs"
        private const val SUBTITLE_PREFIX = "subtitle"
        private const val HEADROOM_BYTES = 50L * 1024L * 1024L
        private const val UNKNOWN_VIDEO_HEADROOM_BYTES = 200L * 1024L * 1024L
        const val MAX_SUBTITLE_BYTES = 8L * 1024L * 1024L
        const val COPY_BUFFER_BYTES = 256 * 1024
    }
}
