package com.burnsubtitle.data.saf

import android.net.Uri

data class SafDocumentInfo(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
) {
    val sizeKnown: Boolean get() = sizeBytes >= 0L
}
