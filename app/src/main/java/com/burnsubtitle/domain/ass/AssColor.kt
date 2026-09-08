package com.burnsubtitle.domain.ass

object AssColor {
    fun fromArgb(argb: Long): String {
        val a = ((argb shr 24) and 0xFF).toInt()
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        val assAlpha = 0xFF - a
        return "&H%02X%02X%02X%02X".format(java.util.Locale.US, assAlpha, b, g, r)
    }

    fun toArgb(ass: String): Long {
        val hex = ass.trim().removePrefix("&H").removePrefix("&h").removeSuffix("&")
        val padded = hex.padStart(8, '0').takeLast(8)
        val value = padded.toLong(16)
        val assAlpha = ((value shr 24) and 0xFF).toInt()
        val b = ((value shr 16) and 0xFF).toInt()
        val g = ((value shr 8) and 0xFF).toInt()
        val r = (value and 0xFF).toInt()
        val a = 0xFF - assAlpha
        return (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }
}
