package com.burnsubtitle.domain.util

object MediaFileNames {
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "mov", "m4v", "avi", "3gp", "3gpp",
        "ts", "m2ts", "mpeg", "mpg", "flv", "wmv",
    )

    fun extension(name: String): String {
        return name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    }

    fun withExtension(baseName: String, extension: String): String {
        val clean = extension.trimStart('.').lowercase()
        return if (clean.isEmpty()) baseName else "$baseName.$clean"
    }

    /**
     * SAF display names are provider-controlled strings. MediaStore rejects DISPLAY_NAME
     * values containing path separators, and they would escape the export directory on
     * the pre-Q FileProvider path.
     */
    fun sanitize(name: String, fallback: String = "video"): String {
        val cleaned = name
            .map { char -> if (char in ILLEGAL_NAME_CHARS || char.isISOControl()) '_' else char }
            .joinToString("")
            .trim()
            .trim('.')
        return cleaned.take(MAX_NAME_LENGTH).ifBlank { fallback }
    }

    fun isVideoFile(displayName: String, mimeType: String?): Boolean {
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        if (mime.startsWith("video/")) return true
        if (mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("text/")) {
            return false
        }
        return extension(displayName) in VIDEO_EXTENSIONS
    }

    private const val MAX_NAME_LENGTH = 120
    private val ILLEGAL_NAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
}
