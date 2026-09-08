package com.burnsubtitle.ui.picker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

class PersistableOpenDocument : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val mimeTypes = input.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            if (mimeTypes.size == 1) {
                type = mimeTypes.first()
            } else {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
            }
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}

object SafMimeTypes {
    val VIDEO = arrayOf("video/*")

    val SUBTITLE = arrayOf(
        "application/x-subrip",
        "application/srt",
        "text/srt",
        "text/x-subrip",
        "text/vtt",
        "text/webvtt",
        "text/x-ssa",
        "application/x-ass",
        "text/x-ass",
        "application/x-ssa",
        "text/plain",
        "application/octet-stream",
        "*/*",
    )
}
