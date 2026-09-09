package app.aaps.plugins.source.instara

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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

    private suspend fun insertedValues(data: String): List<GV> {
        whenever(instaraPlugin.isEnabled()).thenReturn(true)
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(null)
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(PersistenceLayer.TransactionResult())
        worker(data).doWorkAndLog()
        val captor = argumentCaptor<List<GV>>()
        verify(persistenceLayer).insertCgmSourceData(anyOrNull(), captor.capture(), anyOrNull(), anyOrNull())
        return captor.firstValue
    }

    @Test
    fun `mmol units are normalized to mgdl`() = runTest {
        val values = insertedValues("""[{"sgvId":"1234567890123","date":1000,"current":5.0,"units":"mmol"}]""")
        assertThat(values[0].value).isWithin(0.001).of(5.0 * Constants.MMOLL_TO_MGDL)
    }

    @Test
    fun `low value without units is inferred as mmol`() = runTest {
        val values = insertedValues("""[{"sgvId":"1234567890123","date":1000,"current":5.0}]""")
        assertThat(values[0].value).isWithin(0.001).of(5.0 * Constants.MMOLL_TO_MGDL)
    }

    @Test
    fun `explicit mgdl units are kept`() = runTest {
        val values = insertedValues("""[{"sgvId":"1234567890123","date":1000,"current":123.0,"units":"mg/dl"}]""")
        assertThat(values[0].value).isWithin(0.001).of(123.0)
    }

    @Test
    fun `duplicate sgvId in same batch is inserted once`() = runTest {
        val values = insertedValues(
            """[{"sgvId":"1234567890123","date":1000,"current":100.0},{"sgvId":"1234567890123","date":1001,"current":101.0}]"""
        )
        assertThat(values).hasSize(1)
    }

    @Test
    fun `record already in db is skipped`() = runTest {
        whenever(instaraPlugin.isEnabled()).thenReturn(true)
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any()))
            .thenReturn(GV(timestamp = 0L, value = 100.0, raw = null, noise = null, trendArrow = TrendArrow.NONE, sourceSensor = SourceSensor.INSTARA))

        val result = worker("""[{"sgvId":"1234567890123","date":1000,"current":100.0}]""").doWorkAndLog()

        Assertions.assertEquals(ListenableWorker.Result.success(workDataOf("Result" to "No insertable records")), result)
        verify(persistenceLayer, never()).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `recent record triggers overview refresh`() = runTest {
        whenever(instaraPlugin.isEnabled()).thenReturn(true)
        whenever(persistenceLayer.getGlucoseValueByPumpIdAndSource(any(), any())).thenReturn(null)
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(PersistenceLayer.TransactionResult())

        val now = System.currentTimeMillis()
        worker("""[{"sgvId":"1234567890123","date":$now,"current":100.0}]""").doWorkAndLog()

        verify(mockedRxBus).send(any())
    }
}
