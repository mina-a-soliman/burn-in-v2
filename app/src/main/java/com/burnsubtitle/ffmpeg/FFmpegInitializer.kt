package com.burnsubtitle.ffmpeg

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class FFmpegRuntime(
    val fontsDir: File,
    val fontConfigFile: File,
)

@Singleton
class FFmpegInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: FFmpegEngine,
    private val fonts: FontRuntime,
) {
    @Volatile
    private var runtime: FFmpegRuntime? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun warmUp() {
        scope.launch { runCatching { prepare() } }
    }

    @Synchronized
    fun prepare(): FFmpegRuntime {
        runtime?.let { return it }
        engine.load()
        val fontsDir = fonts.prepare(context)
        val config = File(context.filesDir, "fontconfig/fonts.conf")
        engine.configureFonts(config.takeIf { it.exists() }?.absolutePath, fontsDir.absolutePath)
        val prepared = FFmpegRuntime(fontsDir = fontsDir, fontConfigFile = config)
        runtime = prepared
        return prepared
    }

    @Synchronized
    fun reset() {
        runtime = null
    }
}
