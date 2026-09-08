package com.burnsubtitle.ui.result

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.burnsubtitle.domain.model.BurnResult
import com.burnsubtitle.domain.session.BurnSession
import com.burnsubtitle.domain.usecase.ResetSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val session: BurnSession,
    private val resetSession: ResetSessionUseCase,
) : ViewModel() {
    val result: StateFlow<BurnResult?> = session.result

    fun startOver() {
        resetSession()
    }

    fun shareIntent(): Intent? = successIntent(Intent.ACTION_SEND)

    fun viewIntent(): Intent? = successIntent(Intent.ACTION_VIEW)

    private fun successIntent(action: String): Intent? {
        val success = session.result.value as? BurnResult.Success ?: return null
        val uri = Uri.parse(success.outputUri)
        return Intent(action).apply {
            if (action == Intent.ACTION_SEND) {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
            } else {
                setDataAndType(uri, "video/mp4")
            }
            clipData = ClipData.newRawUri(success.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
