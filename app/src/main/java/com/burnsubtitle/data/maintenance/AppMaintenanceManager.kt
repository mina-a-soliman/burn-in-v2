package com.burnsubtitle.data.maintenance

import android.content.Context
import com.burnsubtitle.domain.subtitle.SubtitleFonts
import com.burnsubtitle.ffmpeg.FFmpegInitializer
import com.burnsubtitle.ffmpeg.FontRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Metrics resulting from the maintenance routine.
 */
data class MaintenanceStats(
    val bytesFreed: Long,
    val fontsRestored: Int,
    val fontConfigRebuilt: Boolean,
)

@Singleton
class AppMaintenanceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fontRuntime: FontRuntime,
    private val ffmpegInitializer: FFmpegInitializer,
) {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Wipes [context.cacheDir], purges [context.filesDir]/fontconfig and [context.cacheDir]/fontconfig,
     * resets [context.filesDir]/fonts by copying all default .ttf font files from the APK assets,
     * and regenerates fonts.conf for libass.
     */
    suspend fun clearCacheAndResetFonts(): Result<MaintenanceStats> = withContext(ioDispatcher) {
        runCatching {
            // 1. Wipe cache contents and compute freed bytes
            val freedBytes = wipeCacheContents(context.cacheDir)

            // 2. Delete fontconfig directories in filesDir and cacheDir
            val fontConfigDir = File(context.filesDir, FONTCONFIG_DIR)
            if (fontConfigDir.exists()) {
                fontConfigDir.deleteRecursively()
            }
            val fontConfigCacheDir = File(context.cacheDir, FONTCONFIG_DIR)
            if (fontConfigCacheDir.exists()) {
                fontConfigCacheDir.deleteRecursively()
            }

            // 3. Reset files/fonts and re-copy all .ttf fonts from APK assets/fonts
            val fontsDir = File(context.filesDir, FONTS_DIR)
            if (fontsDir.exists()) {
                fontsDir.deleteRecursively()
            }
            if (!fontsDir.mkdirs() && !fontsDir.isDirectory) {
                throw IOException("Unable to create fonts directory: ${fontsDir.absolutePath}")
            }

            val fontsRestored = copyFontAssets(targetDir = fontsDir)

            // 4. Regenerate fonts.conf and ensure fontconfig cache exists
            fontRuntime.prepare(context)

            // 5. Reset cached FFmpeg native runtime font paths
            ffmpegInitializer.reset()

            MaintenanceStats(
                bytesFreed = freedBytes,
                fontsRestored = fontsRestored,
                fontConfigRebuilt = File(fontConfigDir, FONTS_CONF_NAME).exists(),
            )
        }
    }

    private fun wipeCacheContents(dir: File): Long {
        var totalBytes = 0L
        val children = dir.listFiles() ?: return 0L
        for (file in children) {
            totalBytes += calculateSize(file)
            file.deleteRecursively()
        }
        return totalBytes
    }

    private fun calculateSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.walkTopDown().forEach { f ->
            if (f.isFile) size += f.length()
        }
        return size
    }

    private fun copyFontAssets(targetDir: File): Int {
        val assetFontFiles = runCatching { context.assets.list(ASSET_FONTS_DIR) }
            .getOrNull()
            ?.filter { it.endsWith(".ttf", ignoreCase = true) }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(SubtitleFonts.ARABIC_FILE, SubtitleFonts.LATIN_FILE)

        var count = 0
        val buffer = ByteArray(BUFFER_SIZE)

        for (fontName in assetFontFiles) {
            val assetPath = "$ASSET_FONTS_DIR/$fontName"
            val targetFile = File(targetDir, fontName)

            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            count++
        }

        // Ensure default.ttf fallback exists for libass
        val arabicSource = File(targetDir, SubtitleFonts.ARABIC_FILE)
        val defaultFont = File(targetDir, DEFAULT_FONT_NAME)
        if (arabicSource.exists() && (!defaultFont.exists() || defaultFont.length() != arabicSource.length())) {
            arabicSource.copyTo(defaultFont, overwrite = true)
        }

        return count
    }

    companion object {
        private const val FONTS_DIR = "fonts"
        private const val ASSET_FONTS_DIR = "fonts"
        private const val FONTCONFIG_DIR = "fontconfig"
        private const val FONTS_CONF_NAME = "fonts.conf"
        private const val DEFAULT_FONT_NAME = "default.ttf"
        private const val BUFFER_SIZE = 32 * 1024
    }
}
