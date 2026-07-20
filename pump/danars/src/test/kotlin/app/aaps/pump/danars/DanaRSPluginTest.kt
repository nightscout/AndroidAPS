package app.aaps.pump.danars

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.keys.DanaStringNonKey
import com.google.common.truth.Truth.assertThat
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

    @Test
    fun bolusIsClampedToPumpMaxBolus() {
        danaPump.maxBolus = 3.0
        assertThat(danaRSPlugin.applyBolusConstraints(PumpInsulin(10.0)).cU).isWithin(0.0001).of(3.0)
        // Under the limit passes through unchanged.
        assertThat(danaRSPlugin.applyBolusConstraints(PumpInsulin(2.0)).cU).isWithin(0.0001).of(2.0)
    }

    @Test
    fun extendedBolusUsesTheSameLimitAsBolus() {
        danaPump.maxBolus = 3.0
        assertThat(danaRSPlugin.applyExtendedBolusConstraints(PumpInsulin(10.0)).cU).isWithin(0.0001).of(3.0)
    }

    @Test
    fun isConfiguredRequiresBothNameAndAddress() {
        // Defaults (both preferences stubbed empty) → not configured.
        assertThat(danaRSPlugin.isConfigured()).isFalse()

        whenever(preferences.get(DanaStringNonKey.RsName)).thenReturn("UHH00002TI")
        whenever(preferences.get(DanaStringNonKey.MacAddress)).thenReturn("00:11:22:33:44:55")
        danaRSPlugin.changePump() // loads mDeviceName / mDeviceAddress from preferences

        assertThat(danaRSPlugin.isConfigured()).isTrue()
    }

    @Test
    fun isInitializedIsFalseWhileUnconfigured() {
        danaPump.lastConnection = 1
        danaPump.maxBasal = 1.0
        // isConfigured() is false (no device), so isInitialized() must be false regardless of the rest.
        assertThat(danaRSPlugin.isInitialized()).isFalse()
    }

    @Test
    fun isSuspendedReflectsPumpState() {
        assertThat(danaRSPlugin.isSuspended()).isFalse()

        danaPump.pumpSuspended = true
        assertThat(danaRSPlugin.isSuspended()).isTrue()

        danaPump.pumpSuspended = false
        danaPump.errorState = DanaPump.ErrorState.SUSPENDED
        assertThat(danaRSPlugin.isSuspended()).isTrue()
    }

    @Test
    fun handshakeIsNeverReportedInProgress() {
        assertThat(danaRSPlugin.isHandshakeInProgress()).isFalse()
    }

    @Test
    fun baseBasalRateComesFromDanaPump() {
        danaPump.currentBasal = 0.75
        assertThat(danaRSPlugin.baseBasalRate.cU).isWithin(0.0001).of(0.75)
    }

    @Test
    fun serialNumberComesFromDanaPump() {
        danaPump.serialNumber = "ABC12345XY"
        assertThat(danaRSPlugin.serialNumber()).isEqualTo("ABC12345XY")
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