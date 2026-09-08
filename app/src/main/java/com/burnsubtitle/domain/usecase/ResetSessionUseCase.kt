package com.burnsubtitle.domain.usecase

import android.net.Uri
import com.burnsubtitle.data.saf.SafUriPermissions
import com.burnsubtitle.domain.session.BurnSession
import javax.inject.Inject

/**
 * Clears the session for a fresh pick.
 *
 * Picking a file takes a persistable read grant, and picking a replacement releases the
 * one before it. "Start over" has no replacement to compare against, so without this the
 * grants accumulate for the lifetime of the install until the platform cap drops them.
 */
class ResetSessionUseCase @Inject constructor(
    private val session: BurnSession,
    private val permissions: SafUriPermissions,
) {
    operator fun invoke() {
        session.video.value?.contentUri?.let(::releaseGrant)
        session.subtitle.value?.contentUri?.let(::releaseGrant)
        session.resetAll()
    }

    private fun releaseGrant(contentUri: String) {
        if (contentUri.isBlank()) return
        runCatching { Uri.parse(contentUri) }.getOrNull()?.let(permissions::release)
    }
}
