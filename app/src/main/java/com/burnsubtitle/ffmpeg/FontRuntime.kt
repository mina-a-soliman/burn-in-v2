package com.burnsubtitle.ffmpeg

import android.content.Context
import com.burnsubtitle.domain.error.FileSelectionException
import com.burnsubtitle.domain.subtitle.SubtitleFonts
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontRuntime @Inject constructor() {

    fun prepare(context: Context): File {
        val fontsDir = File(context.filesDir, FONTS_DIR)
        fontsDir.mkdirs()
        FONT_ASSETS.forEach { assetName ->
            val out = File(fontsDir, assetName)
            val assetPath = "$ASSET_FONTS/$assetName"
            val assetSize = try {
                context.assets.openFd(assetPath).use { it.length }
            } catch (_: Exception) {
                -1L
            }
            if (out.exists() && out.length() > 0L && (assetSize <= 0L || out.length() == assetSize)) {
                return@forEach
            }
            try {
                context.assets.open(assetPath).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (error: FileNotFoundException) {
                throw FileSelectionException.FontsMissing(error)
            }
            if (!out.exists() || out.length() <= 0L) {
                throw FileSelectionException.FontsMissing()
            }
        }
        val configDir = File(context.filesDir, "fontconfig")
        configDir.mkdirs()
        val config = File(configDir, "fonts.conf")
        val configBody = context.assets.open("fontconfig/fonts.conf").bufferedReader().readText()
            .replace("/data/local/tmp/fonts", fontsDir.absolutePath)
            .replace("/data/local/tmp/fontconfig-cache", File(context.cacheDir, "fontconfig").absolutePath)
        config.writeText(configBody)
        File(context.cacheDir, "fontconfig").mkdirs()
        return fontsDir
    }

    companion object {
        const val FONTS_DIR = "fonts"
        const val ASSET_FONTS = "fonts"
        val FONT_ASSETS = listOf(
            SubtitleFonts.ARABIC_FILE,
            SubtitleFonts.LATIN_FILE,
        )
    }
}
