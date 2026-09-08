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

    fun takePersistableReadWrite(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.isSuccess
    }

    fun releasePersistableReadWrite(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        }
    }

    fun releaseReadWriteIfDifferent(previous: String?, next: Uri) {
        if (previous.isNullOrBlank()) return
        val oldUri = runCatching { Uri.parse(previous) }.getOrNull() ?: return
        if (oldUri == next) return
        releasePersistableReadWrite(oldUri)
    }

    fun isPersistedPermissionValid(uri: Uri, write: Boolean = true): Boolean {
        return runCatching {
            context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri == uri && (!write || perm.isWritePermission) && perm.isReadPermission
            }
        }.getOrDefault(false)
    }
}
