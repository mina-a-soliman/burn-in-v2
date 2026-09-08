package com.burnsubtitle.ffmpeg

import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FFmpegCommandFactoryTest {
    private val factory = FFmpegCommandFactory()
    private val job = BurnJob(
        id = "job",
        videoCachePath = "/data/data/com.burnsubtitle/cache/in.mp4",
        assPath = "/data/data/com.burnsubtitle/cache/styled.ass",
        outputPath = "/data/data/com.burnsubtitle/cache/out.mp4",
        fontsDir = "/data/data/com.burnsubtitle/files/fonts",
        style = SubtitleStyle(),
        videoWidth = 1920,
        videoHeight = 1080,
        durationMs = 10_000,
        displayName = "out.mp4",
    )

    @Test
    fun burnsAssWithArabicFontsAndQualitySettings() {
        val args = factory.build(job)
        val filter = args[args.indexOf("-vf") + 1]
        assertTrue(filter.startsWith("subtitles="))
        assertTrue(filter.contains("filename=/data/data/com.burnsubtitle/cache/styled.ass"))
        assertTrue(filter.contains("fontsdir=/data/data/com.burnsubtitle/files/fonts"))
        assertTrue(filter.contains("charenc=UTF-8"))
        assertTrue(filter.contains("original_size=1920x1080"))
        assertEquals("libx264", args[args.indexOf("-c:v") + 1])
        assertEquals("18", args[args.indexOf("-crf") + 1])
        assertEquals("copy", args[args.indexOf("-c:a") + 1])
        assertTrue(args.contains("-sn"))
        assertTrue(args.contains("+faststart"))
        assertFalse(args.contains("h264_mediacodec"))
        assertEquals("/data/data/com.burnsubtitle/cache/out.mp4", args.last())
    }

    @Test
    fun escapesFilterSpecialCharacters() {
        val escaped = FFmpegCommandFactory.escapeFilterPath("/tmp/a:b/file.ass")
        assertEquals("/tmp/a\\:b/file.ass", escaped)
    }
}
