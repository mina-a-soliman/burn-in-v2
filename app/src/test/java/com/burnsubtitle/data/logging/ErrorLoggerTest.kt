package com.burnsubtitle.data.logging

import android.content.Context
import com.burnsubtitle.data.pref.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ErrorLoggerTest {

    private val dummyContext = Proxy.newProxyInstance(
        Context::class.java.classLoader,
        arrayOf(Context::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getPackageName" -> "com.burnsubtitle"
            "getFilesDir" -> File(System.getProperty("java.io.tmpdir", "/tmp"))
            else -> null
        }
    } as Context

    private val dummyPrefs = Proxy.newProxyInstance(
        AppPreferences::class.java.classLoader,
        arrayOf(),
    ) { _, _, _ -> null }

    @Test
    fun fileNameStartsWithPrefixAndFormattedDate() {
        val logger = ErrorLogger(dummyContext, dummyPrefs as AppPreferences)
        val date = Date(1773000000000L)
        val fileName = logger.generateFileName(date)

        assertTrue(fileName.startsWith("b-e-"))
        assertTrue(fileName.endsWith(".txt"))

        val expectedTimestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(date)
        assertEquals("b-e-$expectedTimestamp.txt", fileName)
    }

    @Test
    fun reportContainsAllExceptionDataAndContext() {
        val logger = ErrorLogger(dummyContext, dummyPrefs as AppPreferences)
        val testException = IOException("Simulated disk write failure", RuntimeException("Root cause error"))
        val date = Date()
        val extra = mapOf(
            "jobId" to "test-job-123",
            "videoName" to "sample_video.mp4",
            "exitCode" to 64,
        )

        val report = logger.buildErrorReportText(
            date = date,
            error = testException,
            tag = "Test Operation",
            extraDetails = extra,
        )

        assertTrue(report.contains("BURN SUBTITLE ERROR REPORT"))
        assertTrue(report.contains("Operation / Tag:   Test Operation"))
        assertTrue(report.contains("Exception Class:   java.io.IOException"))
        assertTrue(report.contains("Simulated disk write failure"))
        assertTrue(report.contains("jobId: test-job-123"))
        assertTrue(report.contains("videoName: sample_video.mp4"))
        assertTrue(report.contains("exitCode: 64"))
        assertTrue(report.contains("STACK TRACE"))
        assertTrue(report.contains("CAUSED BY (1): java.lang.RuntimeException: Root cause error"))
        assertTrue(report.contains("END OF REPORT"))
    }
}
