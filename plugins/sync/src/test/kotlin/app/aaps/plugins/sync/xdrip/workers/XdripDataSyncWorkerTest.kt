package app.aaps.plugins.sync.xdrip.workers

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.plugins.sync.xdrip.compose.XdripMvvmRepository
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class XdripDataSyncWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var dataSyncSelector: DataSyncSelectorXdrip
    @Mock lateinit var xdripMvvmRepository: XdripMvvmRepository
    @Mock lateinit var workerParameters: WorkerParameters

    private fun worker() =
        XdripDataSyncWorker(context, workerParameters, aapsLogger, fabricPrivacy, dataSyncSelector, activePlugin, xdripMvvmRepository)

    @Test
    fun `uploads and updates queue size`() = runTest {
        whenever(dataSyncSelector.queueSize()).thenReturn(5L)

        val result = worker().doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(dataSyncSelector).doUpload()
        verify(xdripMvvmRepository).updateQueueSize(5L)
    }
}
