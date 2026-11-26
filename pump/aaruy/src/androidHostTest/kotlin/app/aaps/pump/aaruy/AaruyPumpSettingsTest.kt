package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.pump.aaruy.comm.BasicParam
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.keys.AaruyDoublePreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntPreferenceKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** The pump keeps the limits in thousandths and the low reservoir alarm in whole units. */
class AaruyPumpSettingsTest {

    private val preferences: Preferences = mock()

    /** The hard limits for the age, as the user's age setting would give them. */
    private var hardLimitMaxBasal = 2.0
    private var hardLimitMaxBolus = 17.0
    private val hardLimits: HardLimits = mock {
        on { maxBasal() } doAnswer { hardLimitMaxBasal }
        on { maxBolus() } doAnswer { hardLimitMaxBolus }
    }
    private val pump = AaruyPump(mock<AAPSLogger>(), mock<ResourceHelper>(), preferences, mock<DateUtil>(), mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>(), hardLimits)

    /** [maxBolus] is the max bolus setting of AAPS. */
    private fun settings(lowReservoir: Double, maxBolus: Double = 10.0, maxBasal: Double = 2.0) {
        whenever(preferences.get(AaruyDoublePreferenceKey.LowReservoir)).thenReturn(lowReservoir)
        whenever(preferences.get(DoubleKey.SafetyMaxBolus)).thenReturn(maxBolus)
        hardLimitMaxBasal = maxBasal
        whenever(preferences.get(AaruyIntPreferenceKey.BolusStep)).thenReturn(0)
        whenever(preferences.get(AaruyIntPreferenceKey.SoundPrompt)).thenReturn(0)
        whenever(preferences.get(AaruyIntPreferenceKey.SoundAlarm)).thenReturn(0)
    }

    /** The pump answered 15 to 15.5 U. Compared with 15.5 the settings were written again at every status, without end. */
    @Test
    fun aValueThePumpCannotHoldIsWrittenOnce() {
        settings(lowReservoir = 15.5, maxBolus = 10.0005)
        val written = pump.settingsForPump()
        assertEquals(15.0, written.lowAlert, 0.0)
        assertEquals(10.0, written.maxBolus, 0.0)
        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 1, 20.0, 0, 3))
        assertFalse(pump.pumpHasTheSettings(), "the first time it is written")
        pump.handleLoadBasicParam(written)
        assertTrue(pump.pumpHasTheSettings(), "the pump holds what was written")
    }

    /** 0.1 * 1000 is 99.99999 in floating point. The protocol rounds the same way. */
    @Test
    fun aLimitInThousandthsIsNotCutByFloatingPoint() {
        settings(lowReservoir = 20.0, maxBasal = 0.1)
        assertEquals(0.1, pump.settingsForPump().maxBasal, 0.0)
    }

    /**
     * The pump refuses a whole profile with a basal above its max basal (vendor, PR 5589). So the pump gets the hard
     * limit of AAPS for the age, above which AAPS takes no profile and sets no temp basal.
     */
    @Test
    fun theMaxBasalOfThePumpIsTheHardLimitForTheAge() {
        settings(lowReservoir = 20.0, maxBasal = 10.0)
        assertEquals(10.0, pump.maxBasalForPump(), 0.0)
        assertEquals(10.0, pump.settingsForPump().maxBasal, 0.0)
        pump.handleLoadBasicParam(pump.settingsForPump())
        assertEquals(AaruyPump.SettingsGap.NONE, pump.settingsGap())

        // The user is now set as a child: 2 U/h. The pump still holds 10 U/h, so it is written at the next status.
        hardLimitMaxBasal = 2.0
        assertEquals(AaruyPump.SettingsGap.OTHERS, pump.settingsGap())
        assertTrue(pump.settingsToWrite(AaruyPumpState.BASALRATE_RUNNING))
        assertEquals(2.0, pump.settingsForPump().maxBasal, 0.0)
    }

    /** The pump gets the max bolus of AAPS: the setting, or the hard limit for the age when that is lower. */
    @Test
    fun theMaxBolusOfThePumpIsTheMaxBolusOfAaps() {
        settings(lowReservoir = 20.0, maxBolus = 8.0)
        assertEquals(8.0, pump.settingsForPump().maxBolus, 0.0)
        pump.handleLoadBasicParam(pump.settingsForPump())
        assertEquals(AaruyPump.SettingsGap.NONE, pump.settingsGap())

        // The user changes the setting: the pump still holds 8 U, so it is written at the next status.
        settings(lowReservoir = 20.0, maxBolus = 6.5)
        assertEquals(AaruyPump.SettingsGap.OTHERS, pump.settingsGap())
        assertEquals(6.5, pump.settingsForPump().maxBolus, 0.0)

        // A child: the hard limit of 5 U is below the setting.
        hardLimitMaxBolus = 5.0
        assertEquals(5.0, pump.maxBolusForPump(), 0.0)

        // Pregnant: 60 U is more than the pump holds.
        settings(lowReservoir = 20.0, maxBolus = 40.0)
        hardLimitMaxBolus = 60.0
        assertEquals(25.0, pump.maxBolusForPump(), 0.0)

        // Below 1 U the pump keeps 1 U. AAPS still allows no more than its 0.5 U.
        settings(lowReservoir = 20.0, maxBolus = 0.5)
        assertEquals(1.0, pump.maxBolusForPump(), 0.0)
    }

    /**
     * Review #73: the pump runs 0.05 U steps and the user chose 0.025 U. Until the pump reports the new step, a bolus is
     * rounded to 0.05 U: seen on a real pump, set to 0.05 U it refuses 0.125 U. The write still asks for 0.025 U.
     */
    @Test
    fun theBolusStepOfThePumpStaysUntilThePumpReportsTheNewOne() {
        settings(lowReservoir = 20.0)
        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        assertFalse(pump.pumpHasTheSettings())
        assertEquals(0.05, pump.bolusStepUnits, 1e-9)
        assertEquals(1, pump.settingsForPump().bolusStep)

        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 1, 20.0, 0, 3))
        assertTrue(pump.pumpHasTheSettings())
        assertEquals(0.025, pump.bolusStepUnits, 1e-9)
    }

    /**
     * Seen on a real pump (2026-11-09): settings are taken on plain basal and while stopped, refused while a temp basal runs.
     * A new bolus step is taken only while stopped, so a step alone is written only then.
     */
    @Test
    fun settingsAreWrittenOnlyInAStateThatTakesThem() {
        settings(lowReservoir = 20.0)
        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 1, 19.0, 0, 3))
        assertEquals(AaruyPump.SettingsGap.OTHERS, pump.settingsGap())
        assertTrue(pump.settingsToWrite(AaruyPumpState.BASALRATE_RUNNING))
        assertTrue(pump.settingsToWrite(AaruyPumpState.STOP_RUNNING))
        assertFalse(pump.settingsToWrite(AaruyPumpState.TMPBASALRATE_RUNNING))
        assertFalse(pump.settingsToWrite(AaruyPumpState.LARGEDOSE_RUNNING))

        // Only the step differs: the pump would keep its own on plain basal.
        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 2, 20.0, 0, 3))
        assertEquals(AaruyPump.SettingsGap.STEP_ONLY, pump.settingsGap())
        assertFalse(pump.settingsToWrite(AaruyPumpState.BASALRATE_RUNNING))
        assertTrue(pump.settingsToWrite(AaruyPumpState.STOP_RUNNING))

        pump.handleLoadBasicParam(BasicParam(2.0, 10.0, 1, 20.0, 0, 3))
        assertEquals(AaruyPump.SettingsGap.NONE, pump.settingsGap())
        assertFalse(pump.settingsToWrite(AaruyPumpState.BASALRATE_RUNNING))
    }

    /** Taken as written until the pump reports what it holds, at the next connect. The bolus step waits for the pump. */
    @Test
    fun afterAnAppStartTheLimitsAreTheSettingsAndTheStepIsTheCoarserOne() {
        settings(lowReservoir = 30.0, maxBolus = 5.0, maxBasal = 1.2)
        whenever(preferences.get(any<StringNonPreferenceKey>())).thenReturn("")
        pump.loadVarsFromSP()
        assertEquals(5.0, pump.maxBolus, 0.0)
        assertEquals(1.2, pump.maxBasal, 0.0)
        assertEquals(30.0, pump.lowAlert, 0.0)
        assertEquals(0.05, pump.bolusStepUnits, 1e-9)
    }
}
