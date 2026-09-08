package com.burnsubtitle.domain.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicShaperTest {
    @Test
    fun shapesIsolatedAndJoinedBeh() {
        assertEquals("\uFE8F", ArabicShaper.shape("ب"))
        assertEquals("\uFE91\uFE90", ArabicShaper.shape("بب"))
    }

    @Test
    fun shapesLamAlefLigature() {
        assertEquals("\uFEFB", ArabicShaper.shape("لا"))
    }

    @Test
    fun leavesEnglishUnchanged() {
        assertEquals("Hello", ArabicShaper.shape("Hello"))
        val mixed = ArabicShaper.shape("Hi باب")
        assertTrue(mixed.startsWith("Hi "))
        assertTrue(mixed != "Hi باب")
    }
}

class ScriptClassifierTest {
    @Test
    fun splitsMixedArabicAndEnglish() {
        val runs = ScriptClassifier.splitRuns("Hello مرحبا")
        assertEquals(2, runs.size)
        assertEquals(ScriptKind.LATIN, runs[0].kind)
        assertEquals(ScriptKind.ARABIC, runs[1].kind)
        assertTrue(runs[0].text.contains("Hello"))
        assertTrue(runs[1].text.contains("مرحبا"))
    }

    @Test
    fun detectsRtlFromFirstStrongArabic() {
        assertEquals(ParagraphDirection.RTL, TextDirection.detect("مرحبا 12"))
        assertEquals(ParagraphDirection.LTR, TextDirection.detect("Hello مرحبا"))
    }
}

class SubtitleMarkupTest {
    @Test
    fun stripsHtmlAndAssOverrides() {
        assertEquals("Hello", SubtitleMarkup.strip("<i>Hello</i>"))
        assertEquals("Hi", SubtitleMarkup.strip("{\\an8}Hi"))
        assertEquals("A & B", SubtitleMarkup.strip("A &amp; B"))
    }
}
