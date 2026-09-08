package com.burnsubtitle.domain.subtitle

import com.burnsubtitle.domain.ass.AssDocumentWriter
import com.burnsubtitle.domain.ass.AssStyleGenerator
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.SubtitlePosition
import com.burnsubtitle.domain.model.SubtitleStyle
import com.burnsubtitle.domain.parser.SrtParser
import com.burnsubtitle.ffmpeg.FFmpegCommandFactory
import com.burnsubtitle.ffmpeg.FFmpegException
import com.burnsubtitle.ffmpeg.FFmpegFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicSubtitleBurnTest {

    private val parser = SrtParser()
    private val styleGenerator = AssStyleGenerator()
    private val writer = AssDocumentWriter(styleGenerator)
    private val commandFactory = FFmpegCommandFactory()

    @Test
    fun test1_arabicUtf8Srt_generatesAssPreservingArabicAndNoCharenc() {
        val srt = """
            1
            00:00:00,040 --> 00:00:06,359
            لو اختفت هذه الحيوانات الخمسة غدًا، قد لا يموت العالم
        """.trimIndent()

        val doc = parser.parse(srt)
        assertEquals(1, doc.cues.size)
        val cue = doc.cues[0]
        assertEquals(40L, cue.startMs)
        assertEquals(6359L, cue.endMs)
        assertTrue(cue.text.contains("لو اختفت هذه الحيوانات الخمسة غدًا، قد لا يموت العالم"))

        val assText = writer.write(doc, SubtitleStyle(), playResX = 1920, playResY = 1080)

        // Verify UTF-8 ASS structure
        val cleanAss = assText.removePrefix("\uFEFF")
        assertTrue(cleanAss.startsWith("[Script Info]"))
        assertTrue(cleanAss.contains("[V4+ Styles]"))
        assertTrue(cleanAss.contains("[Events]"))

        // Dialogue must preserve Arabic text without corrupt escapes or question marks
        assertTrue(cleanAss.contains("لو اختفت هذه الحيوانات الخمسة غدًا، قد لا يموت العالم"))
        assertFalse(cleanAss.contains("????"))
        assertTrue(cleanAss.contains("{\\fn${SubtitleFonts.ARABIC_FAMILY}}"))

        // Verify byte-level UTF-8 round trip
        val utf8Bytes = assText.toByteArray(Charsets.UTF_8)
        val decoded = SubtitleDecoder.decode(utf8Bytes)
        assertTrue(decoded.contains("لو اختفت هذه الحيوانات الخمسة غدًا، قد لا يموت العالم"))

        // Verify FFmpeg filter does NOT contain charenc
        val job = BurnJob(
            id = "test-job-arabic",
            videoCachePath = "/cache/input.mp4",
            assPath = "/cache/styled.ass",
            outputPath = "/cache/output.mp4",
            fontsDir = "/files/fonts",
            style = SubtitleStyle(),
            videoWidth = 1920,
            videoHeight = 1080,
            durationMs = 6359L,
            displayName = "video_burned.mp4",
        )
        val filter = commandFactory.buildSubtitlesFilter(job)
        assertFalse("subtitles filter must not contain charenc", filter.contains("charenc"))
        assertTrue(filter.contains("fontsdir=/files/fonts"))
        assertTrue(filter.contains("original_size=1920x1080"))
    }

    @Test
    fun test1_overlappingArabicSrt_preservesTimings() {
        val srt = """
            1
            00:00:00,040 --> 00:00:06,359
            لو اختفت هذه الحيوانات الخمسه غدا قد لا

            2
            00:00:03,120 --> 00:00:10,040
            يموت العالم لكن الحياه فيه ستتغير بشكل

            3
            00:00:06,359 --> 00:00:12,960
            مخيف في المركز الخامس الذئاب قد تبدو
        """.trimIndent()

        val doc = parser.parse(srt)
        assertEquals(3, doc.cues.size)

        // Cue 1
        assertEquals(40L, doc.cues[0].startMs)
        assertEquals(6359L, doc.cues[0].endMs)
        assertEquals("لو اختفت هذه الحيوانات الخمسه غدا قد لا", doc.cues[0].text)

        // Cue 2 (overlaps with Cue 1)
        assertEquals(3120L, doc.cues[1].startMs)
        assertEquals(10040L, doc.cues[1].endMs)
        assertEquals("يموت العالم لكن الحياه فيه ستتغير بشكل", doc.cues[1].text)

        // Cue 3 (overlaps with Cue 2)
        assertEquals(6359L, doc.cues[2].startMs)
        assertEquals(12960L, doc.cues[2].endMs)
        assertEquals("مخيف في المركز الخامس الذئاب قد تبدو", doc.cues[2].text)

        val assText = writer.write(doc, SubtitleStyle(), 1920, 1080)
        val lines = assText.lines()
        val dialogues = lines.filter { it.startsWith("Dialogue: ") }
        assertEquals(3, dialogues.size)
        assertTrue(dialogues[0].contains("0:00:00.04,0:00:06.35"))
        assertTrue(dialogues[1].contains("0:00:03.12,0:00:10.04"))
        assertTrue(dialogues[2].contains("0:00:06.35,0:00:12.96"))
    }

    @Test
    fun test2_englishSrt_burnsSuccessfully() {
        val srt = """
            1
            00:00:00,000 --> 00:00:05,000
            Hello world
        """.trimIndent()

        val doc = parser.parse(srt)
        assertEquals(1, doc.cues.size)
        assertEquals(0L, doc.cues[0].startMs)
        assertEquals(5000L, doc.cues[0].endMs)

        val assText = writer.write(doc, SubtitleStyle(), 1920, 1080)
        assertTrue(assText.contains("Hello world"))
        assertTrue(assText.contains("{\\fn${SubtitleFonts.LATIN_FAMILY}}"))
    }

    @Test
    fun test3_mixedArabicEnglish_wrapsWithCorrectBidiAndFonts() {
        val srt = """
            1
            00:00:00,000 --> 00:00:05,000
            Hello العالم
        """.trimIndent()

        val doc = parser.parse(srt)
        val assText = writer.write(doc, SubtitleStyle(), 1920, 1080)

        // Mixed line must assign Noto Sans to Latin and Noto Naskh Arabic to Arabic
        assertTrue(assText.contains(SubtitleFonts.LATIN_FAMILY))
        assertTrue(assText.contains(SubtitleFonts.ARABIC_FAMILY))
        assertTrue(assText.contains("Hello"))
        assertTrue(assText.contains("العالم"))
        // Bidi directional controls RLI/LRI/PDI must be present
        assertTrue(assText.contains(BidiPreprocessor.RLI))
        assertTrue(assText.contains(BidiPreprocessor.LRI))
        assertTrue(assText.contains(BidiPreprocessor.PDI))
    }

    @Test
    fun test4_arabicPunctuationAndNumbers_preservesIntegrity() {
        val text = "المركز الخامس: الذئاب 123"
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            $text
        """.trimIndent()

        val doc = parser.parse(srt)
        val assText = writer.write(doc, SubtitleStyle(), 1920, 1080)
        assertTrue(assText.contains("المركز الخامس: الذئاب 123"))
        assertFalse(assText.contains("???"))
    }

    @Test
    fun test5_differentFonts_matchEmbeddedMetadata() {
        assertEquals("Noto Naskh Arabic", SubtitleFonts.ARABIC_FAMILY)
        assertEquals("Noto Sans", SubtitleFonts.LATIN_FAMILY)
        assertEquals("NotoNaskhArabic-Regular.ttf", SubtitleFonts.ARABIC_FILE)
        assertEquals("NotoSans-Regular.ttf", SubtitleFonts.LATIN_FILE)

        val style = SubtitleStyle(fontFamily = SubtitleFonts.ARABIC_FAMILY)
        val styleLine = styleGenerator.toStyleLine(style, 1920, 1080)
        assertTrue(styleLine.contains("Style: Default,Noto Naskh Arabic,"))

        val latinStyle = SubtitleStyle(fontFamily = SubtitleFonts.LATIN_FAMILY)
        val latinStyleLine = styleGenerator.toStyleLine(latinStyle, 1920, 1080)
        assertTrue(latinStyleLine.contains("Style: Default,Noto Sans,"))
    }

    @Test
    fun test6_verticalVideo_maintainsUprightPlayResAndOriginalSize() {
        val width = 1440
        val height = 2560
        val srt = """
            1
            00:00:00,100 --> 00:00:03,500
            فيديو طولي
        """.trimIndent()

        val doc = parser.parse(srt)
        val assText = writer.write(doc, SubtitleStyle(position = SubtitlePosition.BOTTOM_CENTER), width, height)

        assertTrue(assText.contains("PlayResX: 1440"))
        assertTrue(assText.contains("PlayResY: 2560"))
        assertTrue(assText.contains("LayoutResX: 1440"))
        assertTrue(assText.contains("LayoutResY: 2560"))

        val job = BurnJob(
            id = "vertical-job",
            videoCachePath = "/cache/vertical.mp4",
            assPath = "/cache/styled.ass",
            outputPath = "/cache/vertical_out.mp4",
            fontsDir = "/files/fonts",
            style = SubtitleStyle(),
            videoWidth = width,
            videoHeight = height,
            durationMs = 3500L,
            displayName = "vertical_burned.mp4",
        )
        val filter = commandFactory.buildSubtitlesFilter(job)
        assertTrue(filter.contains("original_size=1440x2560"))
        assertFalse(filter.contains("charenc"))
    }

    @Test
    fun test_ffmpegFailureDiagnosis_accuratelyCategorizesErrors() {
        val iconvLog = """
            [ssa] Character encoding subtitles conversion needs a libavcodec built with iconv support for this codec
            [AVFilterGraph] Error processing filtergraph: Function not implemented
        """.trimIndent()
        assertEquals(FFmpegFailureReason.UNSUPPORTED_ENCODING, FFmpegException.Failed.diagnose(iconvLog))

        val filterLog = "Error processing filtergraph: Invalid argument"
        assertEquals(FFmpegFailureReason.FILTER_FAILURE, FFmpegException.Failed.diagnose(filterLog))

        val codecLog = "Error while opening encoder for output stream: Unknown error"
        assertEquals(FFmpegFailureReason.CODEC_FAILURE, FFmpegException.Failed.diagnose(codecLog))

        val fontLog = "Cannot load font NotoNaskhArabic"
        assertEquals(FFmpegFailureReason.MISSING_FONT, FFmpegException.Failed.diagnose(fontLog))

        val spaceLog = "No space left on device while writing frame"
        assertEquals(FFmpegFailureReason.INSUFFICIENT_STORAGE, FFmpegException.Failed.diagnose(spaceLog))

        val videoLog = "Invalid data found when processing input video.mp4"
        assertEquals(FFmpegFailureReason.INVALID_VIDEO, FFmpegException.Failed.diagnose(videoLog))

        val av1HwaccelLog = """
            [av1 @ 0x7b1234] Your platform doesn't support hardware accelerated AV1 decoding.
            [av1 @ 0x7b1234] Failed to get pixel format.
            [av1 @ 0x7b1234] Get current frame error
            Function not implemented
        """.trimIndent()
        assertEquals(FFmpegFailureReason.CODEC_FAILURE, FFmpegException.Failed.diagnose(av1HwaccelLog))
    }

    @Test
    fun test_videoSource_isAv1Detection() {
        val av1TrackSource = com.burnsubtitle.domain.model.VideoSource(
            contentUri = "content://media/1",
            displayName = "test.mp4",
            durationMs = 5000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            codecMimeType = "video/av01",
        )
        assertTrue(av1TrackSource.isAv1)

        val av1ContainerSource = com.burnsubtitle.domain.model.VideoSource(
            contentUri = "content://media/2",
            displayName = "test.webm",
            durationMs = 5000L,
            width = 1920,
            height = 1080,
            mimeType = "video/av01",
        )
        assertTrue(av1ContainerSource.isAv1)

        val h264Source = com.burnsubtitle.domain.model.VideoSource(
            contentUri = "content://media/3",
            displayName = "test.mp4",
            durationMs = 5000L,
            width = 1920,
            height = 1080,
            mimeType = "video/mp4",
            codecMimeType = "video/avc",
        )
        assertFalse(h264Source.isAv1)
    }
}
