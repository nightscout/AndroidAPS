package app.aaps.plugins.source

import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AidexWorkerTest : TestBaseWithProfile() {

    @Mock lateinit var workerParameters: WorkerParameters
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private val rxBusMock: RxBus = mock()
    private lateinit var aidexPlugin: AidexPlugin
    private lateinit var worker: AidexPlugin.AidexWorker

    private fun inputDataOf(
        timestamp: Long = 1_700_000_000_000L,
        bgType: String = "mg/dl",
        bgValue: Double = 120.0,
        bgSlopeName: String? = "Flat",
        sensorExpired: Boolean = false,
        sensorError: Boolean = false,
        sensorStabilizing: Boolean = false,
        replaceSensor: Boolean = false,
        signalLost: Boolean = false,
    ) = workDataOf(
        Intents.AIDEX_TIMESTAMP to timestamp,
        Intents.AIDEX_BG_TYPE to bgType,
        Intents.AIDEX_BG_VALUE to bgValue,
        Intents.AIDEX_BG_SLOPE_NAME to bgSlopeName,
        Intents.AIDEX_SENSOR_EXPIRED to sensorExpired,
        Intents.EXTRA_SENSOR_ERROR to sensorError,
        Intents.EXTRA_SENSOR_STABILIZING to sensorStabilizing,
        Intents.EXTRA_REPLACE_SENSOR to replaceSensor,
        Intents.EXTRA_SIGNAL_LOST to signalLost,
    )

    @BeforeEach
    fun setup() {
        // Real plugin instance — the worker writes & reads `_hasSensorError` on it; a mock
        // wouldn't preserve that state.
        aidexPlugin = AidexPlugin(
            rh = mock<ResourceHelper>(),
            aapsLogger = aapsLogger,
            preferences = mock<Preferences>(),
            config = mock<Config>(),
            notificationManager = notificationManager,
        )
        whenever(workerParameters.inputData).thenReturn(inputDataOf())
        worker = AidexPlugin.AidexWorker(context, workerParameters, aapsLogger, fabricPrivacy, aidexPlugin, persistenceLayer, rxBusMock)
    }

    private fun reMockInput(data: androidx.work.Data) {
        whenever(workerParameters.inputData).thenReturn(data)
        worker = AidexPlugin.AidexWorker(context, workerParameters, aapsLogger, fabricPrivacy, aidexPlugin, persistenceLayer, rxBusMock)
    }

    @Test
    fun `plugin disabled returns success no-op`() = runTest {
        aidexPlugin.setPluginEnabled(app.aaps.core.data.plugin.PluginType.BGSOURCE, false)

        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success(workDataOf("Result" to "Plugin not enabled")))
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(rxBusMock, never()).send(any())
    }

    @Test
    fun `valid mg per dl reading inserts data and refreshes overview`() = runTest {
        aidexPlugin.setPluginEnabled(app.aaps.core.data.plugin.PluginType.BGSOURCE, true)
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(PersistenceLayer.TransactionResult())

        val result = worker.doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        val expectedGv = GV(
            timestamp = 1_700_000_000_000L,
            value = 120.0,
            raw = null,
            noise = null,
            trendArrow = TrendArrow.FLAT,
            sourceSensor = SourceSensor.AIDEX
        )
        verify(persistenceLayer).insertCgmSourceData(Sources.Aidex, listOf(expectedGv), emptyList(), null)
        verify(rxBusMock).send(any<EventRefreshOverview>())
    }

    @Test
    fun `mmol per l reading is converted to mg per dl before insert`() = runTest {
        aidexPlugin.setPluginEnabled(app.aaps.core.data.plugin.PluginType.BGSOURCE, true)
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(PersistenceLayer.TransactionResult())
        reMockInput(inputDataOf(bgType = "mmol/l", bgValue = 6.0))

        worker.doWork()

        val expectedGv = GV(
            timestamp = 1_700_000_000_000L,
            value = 6.0 * Constants.MMOLL_TO_MGDL,
            raw = null,
            noise = null,
            trendArrow = TrendArrow.FLAT,
            sourceSensor = SourceSensor.AIDEX
        )
        verify(persistenceLayer).insertCgmSourceData(Sources.Aidex, listOf(expectedGv), emptyList(), null)
    }

    @Test
    fun `sensor error blocks insert`() = runTest {
        aidexPlugin.setPluginEnabled(app.aaps.core.data.plugin.PluginType.BGSOURCE, true)
        reMockInput(inputDataOf(sensorError = true))

        worker.doWork()

        assertThat(aidexPlugin.hasSensorError()).isTrue()
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(rxBusMock, never()).send(any())
    }

    @Test
    fun `sensor expired blocks insert and flips hasSensorError`() = runTest {
        aidexPlugin.setPluginEnabled(app.aaps.core.data.plugin.PluginType.BGSOURCE, true)
        reMockInput(inputDataOf(sensorExpired = true))

        worker.doWork()

        assertThat(aidexPlugin.hasSensorError()).isTrue()
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `zero bg value blocks insert`() = runTest {
        aidexPlugin.setPluginEnabled(app.aaps.core.data.plugin.PluginType.BGSOURCE, true)
        reMockInput(inputDataOf(bgValue = 0.0))

        worker.doWork()

        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(rxBusMock, never()).send(any())
    }

    @Test
    fun `hasSensorError clears after a clean reading following a faulted one`() = runTest {
        aidexPlugin.setPluginEnabled(app.aaps.core.data.plugin.PluginType.BGSOURCE, true)
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(PersistenceLayer.TransactionResult())

        // First: faulted reading sets the flag and blocks insert
        reMockInput(inputDataOf(sensorError = true))
        worker.doWork()
        assertThat(aidexPlugin.hasSensorError()).isTrue()

        // Then: clean reading clears the flag and inserts
        reMockInput(inputDataOf())
        worker.doWork()
        assertThat(aidexPlugin.hasSensorError()).isFalse()
        verify(persistenceLayer).insertCgmSourceData(eq(Sources.Aidex), any(), any(), anyOrNull())
    }

    @Test
    fun `persistence exception surfaces as Result failure`() = runTest {
        aidexPlugin.setPluginEnabled(app.aaps.core.data.plugin.PluginType.BGSOURCE, true)
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenThrow(RuntimeException("boom"))

        val result = worker.doWork()

        assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }
}
