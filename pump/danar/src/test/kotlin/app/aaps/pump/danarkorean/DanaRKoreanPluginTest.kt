package app.aaps.pump.danarkorean

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaStringNonKey
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class DanaRKoreanPluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase

    lateinit var danaPump: DanaPump

    private lateinit var danaRPlugin: DanaRKoreanPlugin

    @BeforeEach
    fun prepareMocks() {
        whenever(preferences.get(DanaStringNonKey.MacAddress)).thenReturn("")
        whenever(preferences.get(DanaStringNonKey.RName)).thenReturn("")
        whenever(rh.gs(app.aaps.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        whenever(rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)).thenReturn("it must be positive value")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingbasalratio)).thenReturn("Limiting max basal rate to %1\$.2f U/h because of %2\$s")
        whenever(rh.gs(app.aaps.core.ui.R.string.limitingpercentrate)).thenReturn("Limiting max percent rate to %1\$d%% because of %2\$s")
        danaPump = DanaPump(aapsLogger, preferences, dateUtil, decimalFormatter, profileStoreProvider)
        danaRPlugin = DanaRKoreanPlugin(
            aapsLogger, aapsSchedulers, rxBus, context, rh, activePlugin, commandQueue, danaPump, dateUtil, fabricPrivacy,
            pumpSync, preferences, config, notificationManager, danaHistoryDatabase, decimalFormatter, BolusProgressData(ch, rh, CoroutineScope(Dispatchers.Unconfined)), pumpEnactResultProvider
        )
    }

    @Test @Throws(Exception::class)
    fun basalRateShouldBeLimited() {
        danaRPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        // cU-domain limit (PumpPluginConstraints); reasons are logged, not surfaced.
        val result = danaRPlugin.applyBasalConstraints(PumpRate(Double.MAX_VALUE))
        Assertions.assertEquals(0.8, result.cU, 0.01)
    }

}