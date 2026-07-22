package app.aaps.pump.danars

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryDatabase
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.keys.DanaStringComposedKey
import app.aaps.pump.dana.keys.DanaStringNonKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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

    @Test
    fun connectionStateDelegatesToTheServiceAndIsSafeWithoutOne() {
        // No bound service in a unit test → the guarded delegations are no-ops, not crashes.
        assertThat(danaRSPlugin.isConnected()).isFalse()
        assertThat(danaRSPlugin.isConnecting()).isFalse()
        danaRSPlugin.connect("test")          // guard false (empty device) → no-op
        danaRSPlugin.disconnect("test")       // service null → no-op
        danaRSPlugin.stopConnecting()         // service null → no-op
        danaRSPlugin.stopBolusDelivering()    // service null → no-op
    }

    @Test
    fun changePumpLoadsDeviceDetailsFromPreferences() {
        whenever(preferences.get(DanaStringNonKey.RsName)).thenReturn("UHH00002TI")
        whenever(preferences.get(DanaStringNonKey.MacAddress)).thenReturn("00:11:22:33:44:55")

        danaRSPlugin.changePump()

        // mDeviceAddress is private; isConfigured() (name + address both set) proves both loaded.
        // (danaPump.serialNumber is set then cleared by the reset() at the end of changePump.)
        assertThat(danaRSPlugin.mDeviceName).isEqualTo("UHH00002TI")
        assertThat(danaRSPlugin.isConfigured()).isTrue()
    }

    @Test
    fun getPumpStatusSyncsBasalAndBolusStepIntoTheDescription() {
        danaPump.basalStep = 0.01
        danaPump.bolusStep = 0.05

        runBlocking { danaRSPlugin.getPumpStatus("test") } // service null → just syncs the steps

        assertThat(danaRSPlugin.pumpDescription.basalStep).isWithin(0.0001).of(0.01)
        assertThat(danaRSPlugin.pumpDescription.bolusStep).isWithin(0.0001).of(0.05)
    }

    @Test
    fun historyLoadsReturnUnsuccessfulWithoutAService() {
        assertThat(danaRSPlugin.loadHistory(RecordTypes.RECORD_TYPE_ALARM).success).isFalse()
        assertThat(danaRSPlugin.loadEvents().success).isFalse()
        assertThat(danaRSPlugin.setUserOptions().success).isFalse()
        assertThat(runBlocking { danaRSPlugin.loadTDDs() }.success).isFalse()
    }

    @Test
    fun pumpSpecificShortStatusFormatsTheDailyTotal() {
        danaPump.dailyTotalUnits = 12.3
        danaPump.maxDailyTotalUnits = 80

        assertThat(danaRSPlugin.pumpSpecificShortStatus(veryShort = false)).contains("TDD")
        assertThat(danaRSPlugin.pumpSpecificShortStatus(veryShort = true)).isEmpty()
    }

    @Test
    fun canHandleDstFollowsTheUtcFlag() {
        danaPump.hwModel = 5 // usingUTC = hwModel >= 7
        assertThat(danaRSPlugin.canHandleDST()).isFalse()
        danaPump.hwModel = 9
        assertThat(danaRSPlugin.canHandleDST()).isTrue()
    }

    @Test
    fun clearPairingRemovesEveryStoredKey() {
        danaRSPlugin.mDeviceName = "UHH00002TI"

        danaRSPlugin.clearPairing()

        verify(preferences).remove(DanaStringComposedKey.ParingKey, "UHH00002TI")
        verify(preferences).remove(DanaStringComposedKey.V3ParingKey, "UHH00002TI")
        verify(preferences).remove(DanaStringComposedKey.V3RandomParingKey, "UHH00002TI")
        verify(preferences).remove(DanaStringComposedKey.V3RandomSyncKey, "UHH00002TI")
        verify(preferences).remove(DanaStringComposedKey.Ble5PairingKey, "UHH00002TI")
    }

    @Test
    fun preferenceScreenContentIsTheDanaRsSubScreen() {
        assertThat(danaRSPlugin.getPreferenceScreenContent().key).isEqualTo("danars_settings")
    }

    @Test
    fun profileIsTriviallySetWhileUninitialized() {
        // Unconfigured pump → isInitialized() false → nothing to compare against, so "already set".
        assertThat(danaRSPlugin.isThisProfileSet(mock())).isTrue()
    }

    @Test
    fun setNewBasalProfileIsDeferredWhileUninitialized() {
        // Not initialized → deferred (re-pushed on reconnect): success=true keeps it out of the
        // central failure alarm, enacted stays false so nothing is shown.
        val result = runBlocking { danaRSPlugin.setNewBasalProfile(mock()) }
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }

    // Command-method logic (percent computation, branch selection, result building) runs even with
    // no bound service — the service call is a no-op and the result is built around it. The full
    // round-trip to a pump is covered by the UI E2E; these cover the branches it does not vary.

    @Test
    fun tempBasalAbsoluteAtBaseRateIsATempOff() {
        danaPump.currentBasal = 1.0 // baseBasalRate
        // Requested == base → 100% → doTempOff, nothing running → a no-op "success".
        val result = runBlocking {
            danaRSPlugin.setTempBasalAbsolute(1.0, 30, enforceNew = false, tbrType = PumpSync.TemporaryBasalType.NORMAL)
        }
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
        assertThat(result.percent).isEqualTo(100)
    }

    @Test
    fun tempBasalAbsoluteBelowBaseComputesALowPercent() {
        danaPump.currentBasal = 1.0
        // 0.5 U/h against a 1.0 base → ~50% → doLowTemp path; without a service the send fails, so
        // this exercises the computation + failure result, not a successful set.
        val result = runBlocking {
            danaRSPlugin.setTempBasalAbsolute(0.5, 30, enforceNew = true, tbrType = PumpSync.TemporaryBasalType.NORMAL)
        }
        assertThat(result.success).isFalse()
    }

    @Test
    fun tempBasalAbsoluteAboveBaseComputesAHighPercent() {
        danaPump.currentBasal = 1.0
        val result = runBlocking {
            danaRSPlugin.setTempBasalAbsolute(2.0, 30, enforceNew = true, tbrType = PumpSync.TemporaryBasalType.NORMAL)
        }
        assertThat(result.success).isFalse()
    }

    @Test
    fun setTempBasalPercentWithoutServiceFails() {
        val result = runBlocking {
            danaRSPlugin.setTempBasalPercent(150, 60, enforceNew = true, tbrType = PumpSync.TemporaryBasalType.NORMAL)
        }
        assertThat(result.success).isFalse()
    }

    @Test
    fun setTempBasalPercentRejectsNegativeInput() {
        val result = runBlocking {
            danaRSPlugin.setTempBasalPercent(-1, 60, enforceNew = true, tbrType = PumpSync.TemporaryBasalType.NORMAL)
        }
        assertThat(result.success).isFalse()
    }

    @Test
    fun setExtendedBolusWithoutServiceFails() {
        val result = runBlocking { danaRSPlugin.setExtendedBolus(1.0, 60) }
        assertThat(result.success).isFalse()
    }

    @Test
    fun cancelTempBasalIsANoOpSuccessWhenNoneRunning() {
        val result = runBlocking { danaRSPlugin.cancelTempBasal(enforceNew = false) }
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
    }

    @Test
    fun cancelExtendedBolusIsANoOpSuccessWhenNoneRunning() {
        val result = runBlocking { danaRSPlugin.cancelExtendedBolus() }
        assertThat(result.success).isTrue()
        assertThat(result.enacted).isFalse()
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