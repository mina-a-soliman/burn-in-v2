package com.burnsubtitle.ffmpeg

import com.burnsubtitle.domain.model.BurnJob
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleBurnProcessor @Inject constructor(
    private val engine: FFmpegEngine,
    private val commands: FFmpegCommandFactory,
    private val initializer: FFmpegInitializer,
) {
    suspend fun run(job: BurnJob) {
        val runtime = initializer.prepare()
        val args = commands.build(job)
        engine.onLogLine("ffmpeg ${FFmpegCommandFactory.describe(args)}")
        val exit = try {
            engine.execute(job, runtime.fontConfigFile.takeIf { it.exists() }?.absolutePath)
        } catch (error: UnsatisfiedLinkError) {
            throw FFmpegException.NotAvailable(error)
        }
        when {
            engine.isCancelRequested() || exit == FFmpegCommandFactory.EXIT_CANCELLED -> {
                throw FFmpegException.Cancelled()
            }
            exit == FFmpegCommandFactory.EXIT_NOT_LINKED -> throw FFmpegException.NotAvailable()
            exit != 0 -> throw FFmpegException.Failed(exit, engine.lastLogs)
        }
        val output = File(job.outputPath)
        if (!output.exists() || output.length() <= 0L) {
            throw FFmpegException.InvalidOutput()
        }
    }

    fun cancel() {
        engine.cancel()
    }
}
