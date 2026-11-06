package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.keys.AaruyDoublePreferenceKey
import app.aaps.pump.aaruy.keys.AaruyIntPreferenceKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
        assertFalse(pump.syncPrefParam(), "the first time it is written")
        assertEquals(15.0, pump.lowAlert, 0.0)
        assertEquals(10.0, pump.maxBolus, 0.0)
        assertTrue(pump.syncPrefParam(), "the pump holds what was written")
    }

    /** 0.1 * 1000 is 99.99999 in floating point. The protocol rounds the same way. */
    @Test
    fun aLimitInThousandthsIsNotCutByFloatingPoint() {
        settings(lowReservoir = 20.0, maxBasal = 0.1)
        pump.syncPrefParam()
        assertEquals(0.1, pump.maxBasal, 0.0)
    }
}
