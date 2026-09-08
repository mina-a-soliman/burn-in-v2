package com.burnsubtitle.data.work

import com.burnsubtitle.domain.model.BurnJob
import com.burnsubtitle.domain.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BurnWorkerInputTest {

    @Test
    fun burnJobHoldsOutputFolderUri() {
        val jobWithFolder = BurnJob(
            id = "test-id",
            videoCachePath = "/cache/video.mp4",
            assPath = "/cache/sub.ass",
            outputPath = "/cache/out.mp4",
            fontsDir = "/files/fonts",
            style = SubtitleStyle(),
            videoWidth = 1920,
            videoHeight = 1080,
            durationMs = 5000L,
            displayName = "video_burned.mp4",
            outputFolderUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
        )
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ADownload",
            jobWithFolder.outputFolderUri,
        )

        val jobDefault = BurnJob(
            id = "test-id-2",
            videoCachePath = "/cache/video.mp4",
            assPath = "/cache/sub.ass",
            outputPath = "/cache/out.mp4",
            fontsDir = "/files/fonts",
            style = SubtitleStyle(),
            videoWidth = 1920,
            videoHeight = 1080,
            durationMs = 5000L,
            displayName = "video_burned.mp4",
        )
        assertNull(jobDefault.outputFolderUri)
    }

    @Test
    fun keyOutputFolderUriConstantIsPresent() {
        assertEquals("outputFolderUri", BurnWorker.KEY_OUTPUT_FOLDER_URI)
    }

    @Test
    fun burnJobHoldsIsAv1AndInputDataPreservesIt() {
        val av1Job = BurnJob(
            id = "test-id-av1",
            videoCachePath = "/cache/video.mp4",
            assPath = "/cache/sub.ass",
            outputPath = "/cache/out.mp4",
            fontsDir = "/files/fonts",
            style = SubtitleStyle(),
            videoWidth = 1920,
            videoHeight = 1080,
            durationMs = 5000L,
            displayName = "video_burned.mp4",
            isAv1 = true,
        )
        assertEquals(true, av1Job.isAv1)
        val data = BurnWorker.inputData(av1Job)
        assertEquals(true, data.getBoolean(BurnWorker.KEY_IS_AV1, false))

        val regularJob = BurnJob(
            id = "test-id-reg",
            videoCachePath = "/cache/video.mp4",
            assPath = "/cache/sub.ass",
            outputPath = "/cache/out.mp4",
            fontsDir = "/files/fonts",
            style = SubtitleStyle(),
            videoWidth = 1920,
            videoHeight = 1080,
            durationMs = 5000L,
            displayName = "video_burned.mp4",
        )
        assertEquals(false, regularJob.isAv1)
        val regData = BurnWorker.inputData(regularJob)
        assertEquals(false, regData.getBoolean(BurnWorker.KEY_IS_AV1, false))
    }
}
