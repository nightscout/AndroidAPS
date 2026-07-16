package app.aaps.pump.danars

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaStringNonKey
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

@Suppress("SpellCheckingInspection")
class DanaRSPluginTest : DanaRSTestBase() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    @Mock lateinit var temporaryBasalStorage: TemporaryBasalStorage
    @Mock lateinit var danaHistoryDatabase: DanaHistoryDatabase
    @Mock lateinit var blePreCheck: BlePreCheck

    private lateinit var danaRSPlugin: DanaRSPlugin

    @Test
    fun basalRateShouldBeLimited() {
        danaRSPlugin.setPluginEnabledBlocking(PluginType.PUMP, true)
        danaPump.maxBasal = 0.8
        // cU-domain limit (PumpPluginConstraints); reasons are logged, not surfaced.
        val result = danaRSPlugin.applyBasalConstraints(PumpRate(Double.MAX_VALUE))
        Assertions.assertEquals(0.8, result.cU, 0.0001)
    }

    @BeforeEach
    fun prepareMocks() {
        whenever(preferences.get(DanaStringNonKey.RsName)).thenReturn("")
        whenever(preferences.get(DanaStringNonKey.MacAddress)).thenReturn("")
        whenever(rh.gs(eq(app.aaps.core.ui.R.string.limitingbasalratio), anyOrNull(), anyOrNull())).thenReturn("limitingbasalratio")
        whenever(rh.gs(eq(app.aaps.core.ui.R.string.limitingpercentrate), anyOrNull(), anyOrNull())).thenReturn("limitingpercentrate")

        danaRSPlugin =
            DanaRSPlugin(
                aapsLogger, rh, preferences, commandQueue, aapsSchedulers, rxBus, context, danaPump, detailedBolusInfoStorage, temporaryBasalStorage,
                fabricPrivacy, dateUtil, danaHistoryDatabase, decimalFormatter, pumpEnactResultProvider, blePreCheck, bolusProgressData
            )
    }

}