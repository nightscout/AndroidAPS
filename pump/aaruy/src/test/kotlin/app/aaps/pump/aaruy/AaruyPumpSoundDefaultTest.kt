package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.keys.AaruyIntPreferenceKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A pump reports 0, which is prompt and alarm sound on. Settings nobody has touched must not change that. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyPumpSoundDefaultTest {

    @Test
    fun untouchedSettingsKeepThePumpSoundsOn() {
        val preferences = mock(Preferences::class.java)
        `when`(preferences.get(AaruyIntPreferenceKey.SoundPrompt)).thenReturn(AaruyIntPreferenceKey.SoundPrompt.defaultValue)
        `when`(preferences.get(AaruyIntPreferenceKey.SoundAlarm)).thenReturn(AaruyIntPreferenceKey.SoundAlarm.defaultValue)
        val rh = mock(ResourceHelper::class.java)
        `when`(rh.gs(anyInt())).thenReturn("")
        val pump = AaruyPump(
            mock(AAPSLogger::class.java),
            rh,
            preferences,
            mock(DateUtil::class.java),
            mock(PumpSync::class.java),
            mock(TemporaryBasalStorage::class.java),
            mock(BolusProgressData::class.java)
        )
        pump.closeInjectSound = 0

        pump.syncPrefParam()

        assertEquals(0, pump.closeInjectSound)
    }
}
