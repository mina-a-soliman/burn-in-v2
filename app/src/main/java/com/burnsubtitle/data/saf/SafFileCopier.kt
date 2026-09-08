package com.burnsubtitle.data.saf

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.burnsubtitle.domain.error.FileSelectionException
import com.burnsubtitle.domain.subtitle.SubtitleDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafFileCopier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun copyToFile(uri: Uri, destination: File) {
        destination.parentFile?.mkdirs()
        openRead(uri).use { input ->
            destination.outputStream().buffered(TempFileStore.COPY_BUFFER_BYTES).use { output ->
                input.copyTo(output, TempFileStore.COPY_BUFFER_BYTES)
            }
        }
        if (!destination.exists() || destination.length() <= 0L) {
            throw IOException("Copied file is empty: ${destination.absolutePath}")
        }
    }

    /** Applies the same BOM/legacy-charset detection as subtitle selection. */
    fun readText(uri: Uri): String {
        return SubtitleDecoder.decode(readBytes(uri, TempFileStore.MAX_SUBTITLE_BYTES))
    }

    fun readBytes(uri: Uri, maxBytes: Long): ByteArray {
        openRead(uri).use { input ->
            val buffer = ByteArray(DEFAULT_READ_BUFFER)
            val output = java.io.ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    throw FileSelectionException.SubtitleTooLarge()
                }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }

    fun openRead(uri: Uri): InputStream {
        val fromDescriptor = runCatching {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Unable to open file descriptor for $uri")
            ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        }
        if (fromDescriptor.isSuccess) {
            return fromDescriptor.getOrThrow()
        }
        return context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open $uri", fromDescriptor.exceptionOrNull())
    }

    companion object {
        private const val DEFAULT_READ_BUFFER = 16 * 1024
    }
}
