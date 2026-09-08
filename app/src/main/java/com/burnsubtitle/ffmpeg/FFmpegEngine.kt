package com.burnsubtitle.ffmpeg

import androidx.annotation.Keep
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.BurnProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FFmpegEngine @Inject constructor(
    private val progressParser: FFmpegProgressParser,
) {
    private val mutex = Mutex()
    private val _progress = MutableStateFlow(BurnProgress(0f, 0L, 0L))
    val progress: StateFlow<BurnProgress> = _progress.asStateFlow()

    @Volatile
    private var durationMs: Long = 0L

    @Volatile
    private var cancelRequested: Boolean = false

    private val recentLogs = ArrayDeque<String>(MAX_LOG_LINES)

    val lastLogs: String
        get() = synchronized(recentLogs) { recentLogs.joinToString("\n") }

    fun load() {
        synchronized(loadLock) {
            if (loaded) return
            DEPENDENCY_LIBS.forEach { name ->
                runCatching { System.loadLibrary(name) }
            }
            System.loadLibrary(LIBRARY)
            loaded = true
        }
    }

    fun resetForNewJob() {
        cancelRequested = false
        if (loaded) nativeResetCancel()
    }

    fun configureFonts(fontConfigPath: String?, fontsDir: String): Int {
        load()
        return nativeInit(fontConfigPath, fontsDir)
    }

    fun cancel() {
        cancelRequested = true
        if (loaded) nativeCancel()
    }

    fun isCancelRequested(): Boolean = cancelRequested

    suspend fun execute(job: BurnJob, fontConfigPath: String?): Int = mutex.withLock {
        load()
        if (cancelRequested) return FFmpegCommandFactory.EXIT_CANCELLED
        durationMs = job.durationMs
        synchronized(recentLogs) { recentLogs.clear() }
        _progress.value = BurnProgress(0f, 0L, durationMs)
        val init = configureFonts(fontConfigPath, job.fontsDir)
        if (init == FFmpegCommandFactory.EXIT_NOT_LINKED) return init
        if (cancelRequested) return FFmpegCommandFactory.EXIT_CANCELLED
        withContext(Dispatchers.IO) {
            if (cancelRequested) return@withContext FFmpegCommandFactory.EXIT_CANCELLED
            nativeBurn(
                inputPath = job.videoCachePath,
                outputPath = job.outputPath,
                assPath = job.assPath,
                fontsDir = job.fontsDir,
                fontConfigPath = fontConfigPath,
                crf = FFmpegCommandFactory.CRF,
                preset = FFmpegCommandFactory.PRESET,
                durationMs = job.durationMs,
            )
        }
    }

    @Keep
    @Suppress("unused")
    fun onLogLine(line: String) {
        rememberLog(line)
        progressParser.parseDurationMs(line)?.let { parsed ->
            if (parsed > 0L && durationMs <= 0L) durationMs = parsed
        }
        val time = progressParser.parseTimeMs(line) ?: return
        emitProgress(time, line)
    }

    @Keep
    @Suppress("unused")
    fun onNativeProgress(timeMs: Long) {
        emitProgress(timeMs, "")
    }

    private fun emitProgress(timeMs: Long, line: String) {
        val duration = durationMs
        val fraction = if (duration <= 0L) 0f else (timeMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        _progress.value = BurnProgress(fraction, timeMs, duration, line)
    }

    private fun rememberLog(line: String) {
        synchronized(recentLogs) {
            if (recentLogs.size >= MAX_LOG_LINES) recentLogs.removeFirst()
            recentLogs.addLast(line)
        }
    }

    private external fun nativeInit(fontConfigPath: String?, fontsDir: String): Int
    private external fun nativeBurn(
        inputPath: String,
        outputPath: String,
        assPath: String,
        fontsDir: String,
        fontConfigPath: String?,
        crf: Int,
        preset: String,
        durationMs: Long,
    ): Int
    private external fun nativeCancel()
    private external fun nativeResetCancel()

    companion object {
        private const val LIBRARY = "burnffmpeg"
        private const val MAX_LOG_LINES = 40
        private val loadLock = Any()
        @Volatile
        private var loaded = false
        /**
         * Only the libraries actually shipped as shared objects. libass, HarfBuzz, FreeType
         * and FriBidi are linked statically into libavfilter, so naming them here just
         * produced load failures that were swallowed on every startup.
         */
        private val DEPENDENCY_LIBS = listOf(
            "c++_shared",
            "avutil",
            "swresample",
            "swscale",
            "avcodec",
            "avformat",
            "avfilter",
        )
    }
}
