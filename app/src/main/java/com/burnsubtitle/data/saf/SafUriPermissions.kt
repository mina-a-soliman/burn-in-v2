package com.burnsubtitle.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafUriPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun takePersistableRead(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.isSuccess
    }

    fun release(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        }
    }

    fun releaseIfDifferent(previous: String?, next: Uri) {
        if (previous.isNullOrBlank()) return
        val oldUri = runCatching { Uri.parse(previous) }.getOrNull() ?: return
        if (oldUri == next) return
        release(oldUri)
    }
}
