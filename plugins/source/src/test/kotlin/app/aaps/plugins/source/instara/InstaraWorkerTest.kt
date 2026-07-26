package app.aaps.plugins.source.instara

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertIs

class InstaraWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var instaraPlugin: InstaraPlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var mockedRxBus: RxBus
    @Mock lateinit var workerParameters: WorkerParameters

    private fun worker(data: String?) =
        InstaraPlugin.InstaraWorker(context, workerParameters, aapsLogger, fabricPrivacy, instaraPlugin, persistenceLayer, mockedRxBus, preferences)
            .also { whenever(workerParameters.inputData).thenReturn(if (data == null) workDataOf() else workDataOf("data" to data)) }

    @Test
    fun `disabled plugin returns not enabled`() = runTest {
        whenever(instaraPlugin.isEnabled()).thenReturn(false)

        val result = worker("[]").doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(workDataOf("Result" to "Plugin not enabled")), result)
        verify(persistenceLayer, never()).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `missing data returns failure`() = runTest {
        whenever(instaraPlugin.isEnabled()).thenReturn(true)

        val result = worker(null).doWorkAndLog()

        assertIs<ListenableWorker.Result.Failure>(result)
    }

    @Test
    fun `batch with only invalid records inserts nothing`() = runTest {
        whenever(instaraPlugin.isEnabled()).thenReturn(true)

        // sgvId missing/not 13 digits → invalid → skipped
        val result = worker("""[{"date":1000,"current":100.0},{"sgvId":"123","date":1001,"current":101.0}]""").doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(workDataOf("Result" to "No insertable records")), result)
        verify(persistenceLayer, never()).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `valid record is inserted`() = runTest {
        whenever(instaraPlugin.isEnabled()).thenReturn(true)
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(null)
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(PersistenceLayer.TransactionResult())

        // 13-digit sgvId, no sgvMark (avoids worker rescheduling), old date (not recent)
        val result = worker("""[{"sgvId":"1234567890123","date":1000,"current":100.0}]""").doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(), result)
        verify(persistenceLayer).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }
}
