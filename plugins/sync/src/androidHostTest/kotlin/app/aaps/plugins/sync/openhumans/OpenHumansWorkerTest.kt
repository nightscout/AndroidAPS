package app.aaps.plugins.sync.openhumans

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs

class OpenHumansWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var openHumansUploader: OpenHumansUploaderPlugin
    @Mock lateinit var workerParameters: WorkerParameters

    private fun worker() = OpenHumansWorker(context, workerParameters, aapsLogger, fabricPrivacy, openHumansUploader)

    @Test
    fun `successful upload returns success`() = runTest {
        val result = worker().doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(openHumansUploader).uploadData()
    }

    @Test
    fun `upload exception returns failure`() = runTest {
        whenever(openHumansUploader.uploadData()).thenThrow(RuntimeException("boom"))

        val result = worker().doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
    }
}
