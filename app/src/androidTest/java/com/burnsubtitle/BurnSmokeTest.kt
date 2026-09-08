package com.burnsubtitle

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.burnsubtitle.ffmpeg.FontRuntime
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BurnSmokeTest {
    @Test
    fun bundledFontsArePresent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val names = context.assets.list("fonts")?.toList().orEmpty()
        assertTrue(names.contains("NotoNaskhArabic-Regular.ttf"))
        assertTrue(names.contains("NotoSans-Regular.ttf"))
        val fontsDir = FontRuntime().prepare(context)
        assertTrue(fontsDir.exists())
        assertTrue(fontsDir.list()?.contains("NotoNaskhArabic-Regular.ttf") == true)
    }
}
