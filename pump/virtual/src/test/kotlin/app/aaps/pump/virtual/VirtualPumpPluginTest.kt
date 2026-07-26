package app.aaps.pump.virtual

import app.aaps.core.data.model.BS
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.StringKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

class VirtualPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var bolusProgressData: BolusProgressData

    private lateinit var virtualPumpPlugin: VirtualPumpPlugin
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @BeforeEach
    fun prepareMocks() {
        virtualPumpPlugin = VirtualPumpPlugin(
            aapsLogger, rxBus, rh, preferences,
            commandQueue, pumpSync, config, dateUtil, persistenceLayer, pumpEnactResultProvider, ch, insulin, bolusProgressData, testScope
        )
    }

    @Test
    fun refreshConfiguration() {
        whenever(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        assertThat(virtualPumpPlugin.pumpTypeFlow.value).isEqualTo(PumpType.ACCU_CHEK_COMBO)
    }

    @Test
    fun refreshConfigurationTwice() {
        whenever(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        whenever(preferences.get(StringKey.VirtualPumpType)).thenReturn("Accu-Chek Combo")
        virtualPumpPlugin.refreshConfiguration()
        assertThat(virtualPumpPlugin.pumpTypeFlow.value).isEqualTo(PumpType.ACCU_CHEK_COMBO)
    }

    @Test
    fun `requiredPermissions should return empty list`() {
        assertThat(virtualPumpPlugin.requiredPermissions()).isEmpty()
    }

    // Route recording through the AAPSCLIENT (persistenceLayer) branch so serialNumber()/InstanceId
    // (Firebase) is never touched in a plain JVM unit test.
    @Test
    fun `deliverTreatment when stop pressed returns success with the partial delivered amount`() = runBlocking {
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)).thenReturn("OK")
        whenever(rh.gs(app.aaps.core.ui.R.string.stop)).thenReturn("Stop")
        whenever(bolusProgressData.isStopPressed).thenReturn(true) // stop on the very first 0.1U increment
        val info = DetailedBolusInfo().apply { insulin = 1.0 }

        val result = virtualPumpPlugin.deliverTreatment(info)

        // A user cancel is a partial success, NOT a delivery failure (that was the false red-alert bug).
        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(1e-9).of(0.1)
        // and the partial amount actually delivered is what gets recorded.
        val captor = argumentCaptor<BS>()
        verifyBlocking(persistenceLayer) { insertOrUpdateBolus(captor.capture(), eq(Action.BOLUS), eq(Sources.Pump), anyOrNull()) }
        assertThat(captor.firstValue.amount).isWithin(1e-9).of(0.1)
    }

    @Test
    fun `deliverTreatment full delivery records the full amount`() = runBlocking {
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(rh.gs(app.aaps.core.ui.R.string.virtualpump_resultok)).thenReturn("OK")
        whenever(bolusProgressData.isStopPressed).thenReturn(false)
        val info = DetailedBolusInfo().apply { insulin = 0.3 }

        val result = virtualPumpPlugin.deliverTreatment(info)

        assertThat(result.success).isTrue()
        assertThat(result.bolusDelivered).isWithin(1e-9).of(0.3)
        val captor = argumentCaptor<BS>()
        verifyBlocking(persistenceLayer) { insertOrUpdateBolus(captor.capture(), eq(Action.BOLUS), eq(Sources.Pump), anyOrNull()) }
        assertThat(captor.firstValue.amount).isWithin(1e-9).of(0.3)
    }
}
