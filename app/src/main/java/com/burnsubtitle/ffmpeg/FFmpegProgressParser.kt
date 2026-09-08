package com.burnsubtitle.ffmpeg

import javax.inject.Inject

class FFmpegProgressParser @Inject constructor() {
    fun parseTimeMs(line: String): Long? {
        parseClock(TIME, line)?.let { return it }
        OUT_TIME_MS.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { micros ->
            return micros / 1000L
        }
        OUT_TIME.find(line)?.let { match ->
            return clockToMs(
                match.groupValues[1].toLong(),
                match.groupValues[2].toLong(),
                match.groupValues[3].toLong(),
                match.groupValues[4],
            )
        }
        return null
    }

    fun parseDurationMs(line: String): Long? {
        return parseClock(DURATION, line)
    }

    private fun parseClock(pattern: Regex, line: String): Long? {
        val match = pattern.find(line) ?: return null
        return clockToMs(
            match.groupValues[1].toLong(),
            match.groupValues[2].toLong(),
            match.groupValues[3].toLong(),
            match.groupValues[4],
        )
    }

    private fun clockToMs(hours: Long, minutes: Long, seconds: Long, fraction: String): Long {
        val millis = fraction.padEnd(3, '0').take(3).toLong()
        return (((hours * 60) + minutes) * 60 + seconds) * 1000 + millis
    }

    companion object {
        private val TIME = Regex("""time[=:](\d+):(\d{2}):(\d{2})[.](\d{1,3})""")
        private val DURATION = Regex("""Duration:\s*(\d+):(\d{2}):(\d{2})[.](\d{1,3})""")
        private val OUT_TIME_MS = Regex("""out_time_ms=(\d+)""")
        private val OUT_TIME = Regex("""out_time=(\d+):(\d{2}):(\d{2})[.](\d{1,3})""")
    }
}
