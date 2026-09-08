package com.burnsubtitle.data.logging

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.DocumentsContract
import android.util.Log
import com.burnsubtitle.data.pref.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErrorLogger(
    private val context: Context?,
    private val preferences: AppPreferences?,
) {
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        preferences: AppPreferences,
    ) : this(context as Context?, preferences as AppPreferences?)

    internal constructor() : this(null, null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun log(
        error: Throwable,
        tag: String? = null,
        extraDetails: Map<String, Any?>? = null,
    ) {
        runCatching { Log.e(TAG, "[${tag ?: "Error"}] ${error.message}", error) }
        val folderUri = preferences?.getErrorLogsFolderUri() ?: return
        scope.launch {
            writeLogFile(folderUri, error, tag, extraDetails)
        }
    }

    fun logSync(
        error: Throwable,
        tag: String? = null,
        extraDetails: Map<String, Any?>? = null,
    ): Uri? {
        runCatching { Log.e(TAG, "[${tag ?: "Error"}] ${error.message}", error) }
        val folderUri = preferences?.getErrorLogsFolderUri() ?: return null
        return writeLogFile(folderUri, error, tag, extraDetails)
    }

    private fun writeLogFile(
        treeUri: Uri,
        error: Throwable,
        tag: String?,
        extraDetails: Map<String, Any?>?,
    ): Uri? {
        val resolver = context?.contentResolver ?: return null
        return runCatching {
            val now = Date()
            val fileName = generateFileName(now)
            val content = buildErrorReportText(now, error, tag, extraDetails)

            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val fileUri = DocumentsContract.createDocument(
                resolver,
                parentDocUri,
                "text/plain",
                fileName,
            ) ?: throw IOException("DocumentsContract failed to create document in $treeUri")

            resolver.openOutputStream(fileUri)?.use { outStream ->
                outStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(content)
                }
            } ?: throw IOException("Unable to open output stream for $fileUri")

            fileUri
        }.onFailure { writeError ->
            runCatching { Log.e(TAG, "Failed to write error log file to $treeUri", writeError) }
        }.getOrNull()
    }

    fun generateFileName(date: Date): String {
        val timestamp = SimpleDateFormat(DATE_PATTERN_FILE, Locale.US).format(date)
        return "b-e-$timestamp.txt"
    }

    fun buildErrorReportText(
        date: Date,
        error: Throwable,
        tag: String?,
        extraDetails: Map<String, Any?>?,
    ): String = buildString {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val readableFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        appendLine("================================================================================")
        appendLine("BURN SUBTITLE ERROR REPORT")
        appendLine("================================================================================")
        appendLine("Timestamp (Local): ${readableFormat.format(date)}")
        appendLine("Timestamp (ISO):   ${isoFormat.format(date)}")
        if (!tag.isNullOrBlank()) {
            appendLine("Operation / Tag:   $tag")
        }
        appendLine()

        appendLine("APPLICATION & SYSTEM")
        appendLine("--------------------------------------------------------------------------------")
        val packageInfo = runCatching {
            val ctx = context ?: return@runCatching null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            }
        }.getOrNull()

        val packageName = runCatching { context?.packageName }.getOrNull() ?: "com.burnsubtitle"
        val versionName = packageInfo?.versionName ?: "Unknown"
        val versionCode = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: -1L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: -1L
            }
        }.getOrDefault(-1L)

        val osRelease = runCatching { Build.VERSION.RELEASE }.getOrDefault("Unknown")
        val osSdk = runCatching { Build.VERSION.SDK_INT }.getOrDefault(0)
        val manufacturer = runCatching { Build.MANUFACTURER }.getOrDefault("Unknown")
        val model = runCatching { Build.MODEL }.getOrDefault("Unknown")
        val product = runCatching { Build.PRODUCT }.getOrDefault("Unknown")
        val hardware = runCatching { Build.HARDWARE }.getOrDefault("Unknown")
        val board = runCatching { Build.BOARD }.getOrDefault("Unknown")
        val supportedAbis = runCatching { Build.SUPPORTED_ABIS?.joinToString(", ") }.getOrNull() ?: "Unknown"

        appendLine("Package:           $packageName")
        appendLine("App Version:       $versionName ($versionCode)")
        appendLine("Android OS:        $osRelease (API $osSdk)")
        appendLine("Device:            $manufacturer $model ($product)")
        appendLine("Device Hardware:   $hardware / $board")
        appendLine("Supported ABIs:    $supportedAbis")

        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxRamMb = runtime.maxMemory() / (1024 * 1024)
        appendLine("JVM Memory:        ${usedRamMb}MB used / ${maxRamMb}MB max")

        val filesDirStat = runCatching {
            val ctx = context ?: return@runCatching "Unknown"
            val stat = StatFs(ctx.filesDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            "${availableBytes / (1024 * 1024)}MB free / ${totalBytes / (1024 * 1024)}MB total"
        }.getOrDefault("Unknown")
        appendLine("Internal Storage:  $filesDirStat")
        appendLine()

        appendLine("ERROR DETAILS")
        appendLine("--------------------------------------------------------------------------------")
        appendLine("Exception Class:   ${error.javaClass.name}")
        appendLine("Message:           ${error.message ?: "(no message)"}")
        appendLine("Localized Message: ${error.localizedMessage ?: "(no localized message)"}")
        appendLine()

        if (!extraDetails.isNullOrEmpty()) {
            appendLine("CONTEXT & DIAGNOSTICS")
            appendLine("--------------------------------------------------------------------------------")
            for ((key, value) in extraDetails) {
                appendLine("$key: $value")
            }
            appendLine()
        }

        appendLine("STACK TRACE")
        appendLine("--------------------------------------------------------------------------------")
        appendLine(error.stackTraceToString().trimEnd())
        appendLine()

        var cause = error.cause
        var depth = 1
        while (cause != null) {
            appendLine("CAUSED BY ($depth): ${cause.javaClass.name}: ${cause.message}")
            appendLine(cause.stackTraceToString().trimEnd())
            appendLine()
            cause = cause.cause
            depth++
        }

        appendLine("================================================================================")
        appendLine("END OF REPORT")
        appendLine("================================================================================")
    }

    companion object {
        private const val TAG = "ErrorLogger"
        private const val DATE_PATTERN_FILE = "yyyyMMdd-HHmmss-SSS"
    }
}
