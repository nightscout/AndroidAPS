package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.keys.AaruyIntKey
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
        `when`(preferences.get(AaruyIntKey.PumpPrefSoundRemind)).thenReturn(AaruyIntKey.PumpPrefSoundRemind.defaultValue)
        `when`(preferences.get(AaruyIntKey.PumpPrefSoundAlarm)).thenReturn(AaruyIntKey.PumpPrefSoundAlarm.defaultValue)
        val rh = mock(ResourceHelper::class.java)
        `when`(rh.gs(anyInt())).thenReturn("")
        val pump = AaruyPump(
            mock(AAPSLogger::class.java),
            rh,
            preferences,
            mock(DateUtil::class.java),
            mock(PumpSync::class.java),
            mock(TemporaryBasalStorage::class.java),
            mock(DecimalFormatter::class.java)
        )
        pump.closeInjectSound = 0

        pump.syncPrefParam()

        assertEquals(0, pump.closeInjectSound)
    }
}
