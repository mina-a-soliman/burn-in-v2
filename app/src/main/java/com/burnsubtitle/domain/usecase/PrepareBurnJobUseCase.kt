package com.burnsubtitle.domain.usecase

import android.content.Context
import android.net.Uri
import com.burnsubtitle.data.saf.SafFileCopier
import com.burnsubtitle.data.saf.TempFileStore
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.SubtitleSource
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.model.VideoSource
import com.burnsubtitle.domain.subtitle.SubtitleDecoder
import com.burnsubtitle.domain.subtitle.SubtitleEngine
import com.burnsubtitle.domain.subtitle.SubtitleText
import com.burnsubtitle.domain.util.MediaFileNames
import com.burnsubtitle.ffmpeg.FontRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class PrepareBurnJobUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val copier: SafFileCopier,
    private val tempFiles: TempFileStore,
    private val engine: SubtitleEngine,
    private val fontRuntime: FontRuntime,
) {
    suspend operator fun invoke(
        video: VideoSource,
        subtitle: SubtitleSource,
        style: SubtitleStyle,
        outputFolderUri: String? = null,
    ): BurnJob = withContext(Dispatchers.IO) {
        tempFiles.requireSpaceForVideo(video.sizeBytes)
        val jobId = UUID.randomUUID().toString()
        val workDir = tempFiles.createJobDir(jobId)
        try {
            val videoExtension = MediaFileNames.extension(video.displayName).ifBlank { "mp4" }
            val videoFile = File(workDir, "input.$videoExtension")
            copier.copyToFile(Uri.parse(video.contentUri), videoFile)
            val subtitleText = readSubtitle(subtitle)
            val document = engine.parse(subtitleText, subtitle.format)
            val assFile = File(workDir, "styled.ass")
            assFile.writeText(
                engine.toAss(
                    document = document,
                    style = style,
                    playResX = video.width.coerceAtLeast(1),
                    playResY = video.height.coerceAtLeast(1),
                ),
                Charsets.UTF_8,
            )
            val output = File(workDir, "output.mp4")
            BurnJob(
                id = jobId,
                videoCachePath = videoFile.absolutePath,
                assPath = assFile.absolutePath,
                outputPath = output.absolutePath,
                fontsDir = fontRuntime.prepare(context).absolutePath,
                style = style,
                videoWidth = video.width,
                videoHeight = video.height,
                durationMs = video.durationMs,
                displayName = burnedName(video.displayName),
                outputFolderUri = outputFolderUri,
            )
        } catch (cancelled: CancellationException) {
            tempFiles.deleteJobDir(jobId)
            throw cancelled
        } catch (error: Throwable) {
            tempFiles.deleteJobDir(jobId)
            throw error
        }
    }

    private fun readSubtitle(subtitle: SubtitleSource): String {
        val cached = subtitle.cachePath?.let { File(it) }
        if (cached != null && cached.exists() && cached.length() > 0L) {
            // The cache is written as UTF-8, but decode defensively in case it is stale.
            return SubtitleText.normalize(SubtitleDecoder.decode(cached.readBytes()))
        }
        return SubtitleText.normalize(copier.readText(Uri.parse(subtitle.contentUri)))
    }

    private fun burnedName(original: String): String {
        val base = MediaFileNames.sanitize(original.substringBeforeLast('.', original))
        return "${base}_burned.mp4"
    }
}
