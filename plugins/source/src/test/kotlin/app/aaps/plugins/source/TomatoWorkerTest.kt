package app.aaps.plugins.source

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.shared.tests.TestBaseWithProfile
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TomatoWorkerTest : TestBaseWithProfile() {

    private lateinit var worker: TomatoPlugin.TomatoWorker
    @Mock lateinit var tomatoPlugin: TomatoPlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var workerParameters: WorkerParameters

    init {
        addInjector {
            if (it is TomatoPlugin.TomatoWorker) {
                it.aapsLogger = aapsLogger
                it.tomatoPlugin = tomatoPlugin
                it.persistenceLayer = persistenceLayer
            }
        }
    }

    @BeforeEach
    fun setupMock() {
        whenever(workerParameters.inputData).thenReturn(
            workDataOf(
                "com.fanqies.tomatofn.Extras.Time" to 1678886400000L,
                "com.fanqies.tomatofn.Extras.BgEstimate" to 150.0
            )
        )
        worker = TomatoPlugin.TomatoWorker(context, workerParameters)
        worker.tomatoPlugin = tomatoPlugin
        worker.persistenceLayer = persistenceLayer
    }

    @Test
    fun `When plugin disabled then do nothing`() {
        runBlocking {
            whenever(tomatoPlugin.isEnabled()).thenReturn(false)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(workDataOf("Result" to "Plugin not enabled")), result)
            verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), any())
        }
    }

    @Test
    fun `When plugin enabled then insert data`() {
        runBlocking {
            whenever(tomatoPlugin.isEnabled()).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = 1678886400000L,
                value = 150.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.NONE,
                sourceSensor = SourceSensor.LIBRE_1_TOMATO
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.Tomato, listOf(expectedGv), emptyList(), null)
        }
    }
}