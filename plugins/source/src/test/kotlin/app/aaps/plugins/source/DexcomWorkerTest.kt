package app.aaps.plugins.source

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.keys.BooleanKey
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.shared.tests.BundleMock
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

class DexcomWorkerTest : TestBaseWithProfile() {

    private lateinit var worker: DexcomPlugin.DexcomWorker
    @Mock lateinit var dexcomPlugin: DexcomPlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var workerParameters: WorkerParameters
    @Mock lateinit var dataWorkerStorage: DataWorkerStorage

    init {
        addInjector { injector ->
            if (injector is DexcomPlugin.DexcomWorker) {
                injector.aapsLogger = aapsLogger
                injector.dexcomPlugin = dexcomPlugin
                injector.persistenceLayer = persistenceLayer
                injector.dataWorkerStorage = dataWorkerStorage
                injector.dateUtil = dateUtil
                injector.preferences = preferences
                injector.profileUtil = profileUtil
            }
        }
    }

    @BeforeEach
    fun setupMock() {
        whenever(workerParameters.inputData).thenReturn(workDataOf(DataWorkerStorage.STORE_KEY to 1L))
        worker = DexcomPlugin.DexcomWorker(context, workerParameters)
    }

    @Test
    fun `When plugin disabled then return success`() {
        runBlocking {
            whenever(dexcomPlugin.isEnabled()).thenReturn(false)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(workDataOf("Result" to "Plugin not enabled")), result)
            verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), any())
        }
    }

    @Test
    fun `When plugin enabled then insert G6 data`() {
        val timestamp = (now - 60000) / 1000L
        runBlocking {
            whenever(dexcomPlugin.isEnabled()).thenReturn(true)
            whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            val bundle = BundleMock.mocked().apply {
                putString("sensorType", "G6")
                putBundle("glucoseValues", BundleMock.mocked().apply {
                    putBundle("0", BundleMock.mocked().apply {
                        putLong("timestamp", timestamp)
                        putInt("glucoseValue", 150)
                        putString("trendArrow", "FortyFiveDown")
                    })
                })
                putBundle("meters", BundleMock.mocked().apply {
                    putBundle("0", BundleMock.mocked().apply {
                        putLong("timestamp", timestamp)
                        putInt("meterValue", 150)
                    })
                })
                putLong("sensorInsertionTime", timestamp)
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp * 1000,
                value = 150.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.DEXCOM_G6_NATIVE
            )
            val calibration = PersistenceLayer.Calibration(
                timestamp = timestamp * 1000,
                value = 150.0,
                glucoseUnit = GlucoseUnit.MGDL
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.Dexcom, listOf(expectedGv), listOf(calibration), timestamp * 1000)
        }
    }

    @Test
    fun `When plugin enabled then insert G7 data with too old calibrations and insertions`() {
        val timestamp = (now - 60000) / 1000L
        runBlocking {
            whenever(dexcomPlugin.isEnabled()).thenReturn(true)
            whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            val bundle = BundleMock.mocked().apply {
                putString("sensorType", "G7")
                putBundle("glucoseValues", BundleMock.mocked().apply {
                    putBundle("0", BundleMock.mocked().apply {
                        putLong("timestamp", timestamp)
                        putInt("glucoseValue", 150)
                        putString("trendArrow", "FortyFiveDown")
                    })
                })
                putBundle("meters", BundleMock.mocked().apply {
                    putBundle("0", BundleMock.mocked().apply {
                        putLong("timestamp", 10000L)
                        putInt("meterValue", 150)
                    })
                })
                putLong("sensorInsertionTime", 10000L)
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp * 1000,
                value = 150.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.DEXCOM_G7_NATIVE
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.Dexcom, listOf(expectedGv), emptyList(), null)
        }
    }

    @Test
    fun `When plugin enabled then insert unknown Dexcom data`() {
        val timestamp = (now - 60000) / 1000L
        runBlocking {
            whenever(dexcomPlugin.isEnabled()).thenReturn(true)
            whenever(preferences.get(BooleanKey.BgSourceCreateSensorChange)).thenReturn(true)
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
            val bundle = BundleMock.mocked().apply {
                putString("sensorType", "G9")
                putBundle("glucoseValues", BundleMock.mocked().apply {
                    putBundle("0", BundleMock.mocked().apply {
                        putLong("timestamp", timestamp)
                        putInt("glucoseValue", 150)
                        putString("trendArrow", "FortyFiveDown")
                    })
                })
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.success(), result)
            val expectedGv = GV(
                timestamp = timestamp * 1000,
                value = 150.0,
                raw = null,
                noise = null,
                trendArrow = TrendArrow.FORTY_FIVE_DOWN,
                sourceSensor = SourceSensor.DEXCOM_NATIVE_UNKNOWN
            )
            verify(persistenceLayer).insertCgmSourceData(Sources.Dexcom, listOf(expectedGv), emptyList(), null)
        }
    }

    @Test
    fun `When bundle is missing then return failure`() {
        runBlocking {
            whenever(dexcomPlugin.isEnabled()).thenReturn(true)
            whenever(dataWorkerStorage.pickupBundle(1L)).thenReturn(null)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.failure(workDataOf("Error" to "missing input data")), result)
        }
    }

    @Test
    fun `When glucoseValues are missing then return failure`() {
        runBlocking {
            whenever(dexcomPlugin.isEnabled()).thenReturn(true)
            val bundle = BundleMock.mocked().apply {
                putString("sensorType", "G6")
            }
            whenever(dataWorkerStorage.pickupBundle(any())).thenReturn(bundle)

            val result = worker.doWork()

            Assertions.assertEquals(ListenableWorker.Result.failure(workDataOf("Error" to "missing glucoseValues")), result)
        }
    }
}