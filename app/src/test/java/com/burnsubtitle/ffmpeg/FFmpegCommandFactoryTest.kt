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
        assertFalse("subtitles filter must not contain charenc", filter.contains("charenc"))
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

    @Test
    fun escapesSpacesAndEqualsAndPunctuationInPaths() {
        val path = "/storage/emulated/0/My Videos/sub=1,2;[a]'test'.ass"
        val escaped = FFmpegCommandFactory.escapeFilterPath(path)
        assertEquals(
            "/storage/emulated/0/My\\ Videos/sub\\=1\\,2\\;\\[a\\]\\'test\\'.ass",
            escaped,
        )
    }

    @Test
    fun forcesAv1SoftwareDecodeWhenJobIsAv1() {
        val av1Job = job.copy(isAv1 = true)
        val args = factory.build(av1Job)
        val inputIndex = args.indexOf("-i")
        val codecIndex = args.indexOf("-c:v")
        assertTrue("Expected -c:v before -i for input decoding", codecIndex in 0 until inputIndex)
        assertEquals("av1", args[codecIndex + 1])
        assertFalse("Must never use mediacodec hwaccel for AV1", args.contains("-hwaccel"))
        assertFalse(args.contains("mediacodec"))
    }

    @Test
    fun doesNotAddAv1DecoderFlagWhenJobIsNotAv1() {
        val regularJob = job.copy(isAv1 = false)
        val args = factory.build(regularJob)
        val inputIndex = args.indexOf("-i")
        val argsBeforeInput = args.subList(0, inputIndex)
        assertFalse(argsBeforeInput.contains("-c:v"))
    }
}
