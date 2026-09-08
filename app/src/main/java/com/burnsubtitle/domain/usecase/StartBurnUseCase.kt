package com.burnsubtitle.domain.usecase

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.burnsubtitle.data.work.BurnWorker
import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.ffmpeg.SubtitleBurnProcessor
import javax.inject.Inject

class StartBurnUseCase @Inject constructor(
    private val workManager: WorkManager,
    private val processor: SubtitleBurnProcessor,
) {
    operator fun invoke(job: BurnJob): String {
        val request = OneTimeWorkRequestBuilder<BurnWorker>()
            .setId(java.util.UUID.fromString(job.id))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(BurnWorker.inputData(job))
            .addTag(BurnWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return job.id
    }

    fun cancel(jobId: String) {
        processor.cancel()
        workManager.cancelWorkById(java.util.UUID.fromString(jobId))
    }

    companion object {
        // One constant name so a new export replaces a burn that is still running.
        // Keying it by job id made every export a distinct chain, which let two
        // encodes run at once and fight over the notification and the cancel flag.
        const val UNIQUE_WORK_NAME = "burn-export"
    }
}
