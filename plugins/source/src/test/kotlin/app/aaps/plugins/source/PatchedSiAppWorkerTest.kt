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
import kotlinx.coroutines.test.runTest
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

class PatchedSiAppWorkerTest : TestBaseWithProfile() {

    private lateinit var worker: PatchedSiAppPlugin.PatchedSiAppWorker
    @Mock lateinit var patchedSiAppPlugin: PatchedSiAppPlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var workerParameters: WorkerParameters

    init {
        addInjector { injector ->
            if (injector is PatchedSiAppPlugin.PatchedSiAppWorker) {
                injector.aapsLogger = aapsLogger
                injector.patchedSIAppPlugin = patchedSiAppPlugin
                injector.persistenceLayer = persistenceLayer
            }
        }
    }

    @BeforeEach
    fun setupMock() {
        worker = PatchedSiAppPlugin.PatchedSiAppWorker(context, workerParameters)
    }

    @Test
    fun `When plugin disabled then return success`() {
        runTest {
            whenever(patchedSiAppPlugin.isEnabled()).thenReturn(false)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(workDataOf("Result" to "Plugin not enabled")), result)
            verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), any())
        }
    }

    @Test
    fun `When plugin enabled then insert data`() {
        val timestamp = (now - 60000)
        runTest {
            whenever(patchedSiAppPlugin.isEnabled()).thenReturn(true)
            whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(PersistenceLayer.TransactionResult())
            whenever(workerParameters.inputData).thenReturn(
                workDataOf(
                    "collection" to "entries",
                    "data" to JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "sgv")
                                .put("date", timestamp)
                                .put("sgv", 150.0)
                                .put("direction", "FortyFiveDown")
                        )
                        .put(
                            JSONObject()
                                .put("type", "something")
                        )
                        .toString()
                )
            )

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp,
                value = 150.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.SIBIONIC
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.SiBionic, listOf(expectedGv), emptyList(), null)
        }
    }

    @Test
    fun `When collection is missing then return failure`() {
        runTest {
            whenever(patchedSiAppPlugin.isEnabled()).thenReturn(true)
            whenever(workerParameters.inputData).thenReturn(
                workDataOf("wrong" to "data")
            )

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.failure(workDataOf("Error" to "missing collection")), result)
        }
    }

    @Test
    fun `When no entries return failure`() {
        runTest {
            whenever(patchedSiAppPlugin.isEnabled()).thenReturn(true)
            whenever(workerParameters.inputData).thenReturn(
                workDataOf("collection" to "something_else")
            )

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.failure(workDataOf("Error" to "missing input data")), result)
        }
    }
}