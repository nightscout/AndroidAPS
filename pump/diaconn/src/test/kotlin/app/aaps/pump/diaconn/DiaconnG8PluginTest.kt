package app.aaps.pump.diaconn

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.diaconn.database.DiaconnHistoryDatabase
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class DiaconnG8PluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var diaconnHistoryDatabase: DiaconnHistoryDatabase
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var blePreCheck: BlePreCheck

    lateinit var diaconnG8Pump: DiaconnG8Pump

    private lateinit var diaconnG8Plugin: DiaconnG8Plugin

    @BeforeEach
    fun prepareMocks() {
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbolus)).thenReturn("Limiting bolus to %1\$.1f U because of %2\$s")

        diaconnG8Pump = DiaconnG8Pump(aapsLogger, dateUtil, decimalFormatter)
        diaconnG8Plugin = DiaconnG8Plugin(
            aapsLogger, rh, preferences, commandQueue, rxBus, context, diaconnG8Pump,
            pumpSync, detailedBolusInfoStorage, temporaryBasalStorage, fabricPrivacy, dateUtil, aapsSchedulers,
            diaconnHistoryDatabase, pumpEnactResultProvider, BolusProgressData(ch, rh, CoroutineScope(Dispatchers.Unconfined)), blePreCheck
        )
    }

    @Test
    fun basalRateShouldBeLimited() {
        diaconnG8Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        diaconnG8Pump.maxBasal = 0.8
        // cU-domain limit (PumpPluginConstraints); reasons are logged, not surfaced.
        val result = diaconnG8Plugin.applyBasalConstraints(PumpRate(Double.MAX_VALUE))
        Assertions.assertEquals(0.8, result.cU, 0.01)
    }

    @Test
    fun bolusAmountShouldBeLimited() {
        diaconnG8Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        diaconnG8Pump.maxBolus = 5.0
        // cU-domain limit (PumpPluginConstraints); reasons are logged, not surfaced.
        val result = diaconnG8Plugin.applyBolusConstraints(PumpInsulin(Double.MAX_VALUE))
        Assertions.assertEquals(5.0, result.cU, 0.01)
    }

    @Test
    fun extendedBolusAmountShouldBeLimited() {
        diaconnG8Plugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        diaconnG8Pump.maxBolus = 5.0
        val result = diaconnG8Plugin.applyExtendedBolusConstraints(PumpInsulin(Double.MAX_VALUE))
        Assertions.assertEquals(5.0, result.cU, 0.01)
    }

    @Test
    fun isInitializedShouldReturnTrueWhenPumpIsConnected() {
        whenever(preferences.get(DiaconnStringNonKey.Address)).thenReturn("AA:BB:CC:DD:EE:FF")
        whenever(preferences.get(DiaconnStringNonKey.Name)).thenReturn("TestPump")
        diaconnG8Plugin.changePump()
        diaconnG8Pump.lastConnection = System.currentTimeMillis()
        diaconnG8Pump.maxBasal = 1.0
        assertThat(diaconnG8Plugin.isInitialized()).isTrue()
    }

    @Test
    fun isInitializedShouldReturnFalseWhenNotConnected() {
        diaconnG8Pump.lastConnection = 0
        diaconnG8Pump.maxBasal = 0.0
        assertThat(diaconnG8Plugin.isInitialized()).isFalse()
    }

}
