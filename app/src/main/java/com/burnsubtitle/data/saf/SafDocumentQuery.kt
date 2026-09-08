package com.burnsubtitle.data.saf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafDocumentQuery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun query(uri: Uri): SafDocumentInfo {
        var displayName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "file"
        var sizeBytes = -1L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(-1L)
                }
            }
        }
        val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return SafDocumentInfo(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
        )
    }

    fun queryFolderDisplayName(treeUri: Uri): String {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            var displayName: String? = null
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        displayName = cursor.getString(idx)
                    }
                }
            }
            displayName?.takeIf { it.isNotBlank() }
                ?: docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }
        }.getOrElse {
            treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Folder"
        }
    }
}
