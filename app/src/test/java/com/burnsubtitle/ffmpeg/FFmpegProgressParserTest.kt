package com.burnsubtitle.ffmpeg

import org.junit.Assert.assertEquals
import org.junit.Test

class FFmpegProgressParserTest {
    private val parser = FFmpegProgressParser()

    @Test
    fun parsesStatsTime() {
        assertEquals(1230L, parser.parseTimeMs("frame= 10 fps=30 time=00:00:01.23 bitrate=1000kbits/s"))
    }

    @Test
    fun parsesDurationAndProgressMicros() {
        assertEquals(83_450L, parser.parseDurationMs("Duration: 00:01:23.45, start=0.000000, bitrate=1234 kb/s"))
        assertEquals(1500L, parser.parseTimeMs("out_time_ms=1500000"))
    }
}
