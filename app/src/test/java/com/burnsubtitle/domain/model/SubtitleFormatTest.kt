package com.burnsubtitle.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleFormatTest {
    @Test
    fun fromFileNameUsesExtension() {
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromFileName("movie.SRT"))
        assertEquals(SubtitleFormat.VTT, SubtitleFormat.fromFileName("clip.vtt"))
        assertEquals(SubtitleFormat.ASS, SubtitleFormat.fromFileName("show.ssa"))
        assertNull(SubtitleFormat.fromFileName("video.mp4"))
    }

    @Test
    fun fromMimeTypeIgnoresParameters() {
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromMimeType("application/x-subrip; charset=utf-8"))
        assertEquals(SubtitleFormat.VTT, SubtitleFormat.fromMimeType("text/vtt"))
        assertEquals(SubtitleFormat.ASS, SubtitleFormat.fromMimeType("text/x-ssa"))
        assertNull(SubtitleFormat.fromMimeType("video/mp4"))
        assertNull(SubtitleFormat.fromMimeType(null))
    }
}
