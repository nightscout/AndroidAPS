package app.aaps.plugins.source

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.keys.BooleanKey
import app.aaps.shared.tests.TestBaseWithProfile
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PoctechWorkerTest : TestBaseWithProfile() {

    private lateinit var worker: PoctechPlugin.PoctechWorker
    @Mock lateinit var poctechPlugin: PoctechPlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var workerParameters: WorkerParameters

    init {
        addInjector { injector ->
            if (injector is PoctechPlugin.PoctechWorker) {
                injector.aapsLogger = aapsLogger
                injector.poctechPlugin = poctechPlugin
                injector.persistenceLayer = persistenceLayer
            }
        }
    }

    @BeforeEach
    fun setupMock() {
        worker = PoctechPlugin.PoctechWorker(context, workerParameters)
    }

    @Test
    fun `When plugin disabled then return success`() {
        runBlocking {
            whenever(poctechPlugin.isEnabled()).thenReturn(false)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(workDataOf("Result" to "Plugin not enabled")), result)
            verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), any())
        }
    }

    @Test
    fun `When plugin enabled then insert mmol data`() {
        val timestamp = (now - 60000)
        runBlocking {
            whenever(poctechPlugin.isEnabled()).thenReturn(true)
            whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            whenever(workerParameters.inputData).thenReturn(
                workDataOf(
                    "collection" to "entries",
                    "data" to JSONArray()
                        .put(
                            JSONObject()
                                .put("date", timestamp)
                                .put("units", "mmol/L")
                                .put("current", 10.0)
                                .put("raw", 10.0)
                                .put("direction", "FortyFiveDown")
                        )
                        .toString()
                )
            )

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp,
                value = 180.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.POCTECH_NATIVE
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.PocTech, listOf(expectedGv), emptyList(), null)
        }
    }

    @Test
    fun `When plugin enabled then insert mgdl data`() {
        val timestamp = (now - 60000)
        runBlocking {
            whenever(poctechPlugin.isEnabled()).thenReturn(true)
            whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            whenever(workerParameters.inputData).thenReturn(
                workDataOf(
                    "collection" to "entries",
                    "data" to JSONArray()
                        .put(
                            JSONObject()
                                .put("date", timestamp)
                                .put("units", "mgdl")
                                .put("current", 180.0)
                                .put("raw", 180.0)
                                .put("direction", "FortyFiveDown")
                        )
                        .toString()
                )
            )

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp,
                value = 180.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.POCTECH_NATIVE
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.PocTech, listOf(expectedGv), emptyList(), null)
        }
    }

    @Test
    fun `When collection is missing then return failure`() {
        runBlocking {
            whenever(poctechPlugin.isEnabled()).thenReturn(true)
            whenever(workerParameters.inputData).thenReturn(
                workDataOf("wrong" to "data")
            )

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.failure(workDataOf("Error" to "missing data")), result)
        }
    }
}