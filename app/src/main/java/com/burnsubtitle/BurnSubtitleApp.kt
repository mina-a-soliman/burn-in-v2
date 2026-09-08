package com.burnsubtitle

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.burnsubtitle.data.logging.ErrorLogger
import com.burnsubtitle.ffmpeg.FFmpegInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BurnSubtitleApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var ffmpegInitializer: FFmpegInitializer

    @Inject
    lateinit var errorLogger: ErrorLogger

    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()
        ffmpegInitializer.warmUp()
    }

    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                errorLogger.logSync(
                    error = throwable,
                    tag = "Uncaught Exception",
                    extraDetails = mapOf("thread" to thread.name),
                )
            } catch (_: Throwable) {
                // Defensive: never let logger failure suppress original exception
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
