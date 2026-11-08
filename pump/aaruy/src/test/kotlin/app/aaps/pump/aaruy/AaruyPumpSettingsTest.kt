package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.pump.aaruy.comm.BasicParam
import app.aaps.pump.aaruy.keys.AaruyDoublePreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntPreferenceKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** The pump keeps the limits in thousandths and the low reservoir alarm in whole units. */
class AaruyPumpSettingsTest {

    private val preferences: Preferences = mock()
    private val pump = AaruyPump(mock<AAPSLogger>(), mock<ResourceHelper>(), preferences, mock<DateUtil>(), mock<PumpSync>(), mock<TemporaryBasalStorage>(), mock<BolusProgressData>())

    private fun settings(lowReservoir: Double, maxBolus: Double = 10.0, maxBasal: Double = 2.0) {
        whenever(preferences.get(AaruyDoublePreferenceKey.LowReservoir)).thenReturn(lowReservoir)
        whenever(preferences.get(AaruyDoublePreferenceKey.MaxBolus)).thenReturn(maxBolus)
        whenever(preferences.get(AaruyDoublePreferenceKey.MaxBasal)).thenReturn(maxBasal)
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

    /** Each status read writes the user settings, so after an app restart the pump holds them. The bolus step waits for the pump. */
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
