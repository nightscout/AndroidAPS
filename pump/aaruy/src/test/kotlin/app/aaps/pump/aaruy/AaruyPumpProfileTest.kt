package app.aaps.pump.aaruy

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.comm.BasalInfo
import app.aaps.pump.aaruy.comm.BasalItem
import app.aaps.pump.aaruy.comm.BasalItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Seen on a real pump: profile 0.16 U/h all day, the pump holds 0.15 U/h and a daily total of 3.8 U. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyPumpProfileTest {

    private val pump = AaruyPump(
        mock(AAPSLogger::class.java),
        mock(ResourceHelper::class.java),
        mock(Preferences::class.java),
        mock(DateUtil::class.java),
        mock(PumpSync::class.java),
        mock(TemporaryBasalStorage::class.java),
        mock(DecimalFormatter::class.java),
        mock(BolusProgressData::class.java)
    )

    private fun profile(rate: Double): Profile {
        val profile = mock(Profile::class.java)
        `when`(profile.getBasalValues()).thenReturn(arrayOf(Profile.ProfileValue(0, rate)))
        return profile
    }

    private fun inPump(injectPerDay: Double, rate: Double, start: Int = 0) =
        pump.dataClassToByteArray(BasalInfo(BasalItems(injectPerDay, 1, listOf(BasalItem(start, rate)))))

    @Test
    fun aDailyTotalCutToOneDecimalIsStillTheSameProfile() {
        pump.actualBasalProfile = inPump(3.8, 0.15)
        assertTrue(pump.isBasalInPump(profile(0.16)))
    }

    @Test
    fun theBaseRateFollowsTheTimeOfDayAndNotWhatIsDeliveredNow() {
        val items = listOf(BasalItem(0, 0.5), BasalItem(16, 1.0)) // 1.0 U/h from 08:00
        pump.actualBasalProfile = pump.dataClassToByteArray(BasalInfo(BasalItems(18.0, 2, items)))
        pump.handleBasalStatusUpdate(3.0) // a temp basal runs
        assertEquals(0.5, pump.baseBasalRateAt(7 * 3600 + 59 * 60), 0.0)
        assertEquals(1.0, pump.baseBasalRateAt(8 * 3600), 0.0)
    }

    @Test
    fun noKnownProfileGivesNoBaseRate() {
        pump.actualBasalProfile = ByteArray(0)
        assertEquals(0.0, pump.baseBasalRateAt(0), 0.0)
    }

    @Test
    fun anotherRateIsAnotherProfile() {
        pump.actualBasalProfile = inPump(3.8, 0.15)
        assertFalse(pump.isBasalInPump(profile(0.5)))
    }

    @Test
    fun anotherStartTimeIsAnotherProfile() {
        pump.actualBasalProfile = inPump(3.8, 0.15, start = 2)
        assertFalse(pump.isBasalInPump(profile(0.16)))
    }

    @Test
    fun aPumpProfileThatIsNotKnownIsNotTheSame() {
        pump.actualBasalProfile = ByteArray(0)
        assertFalse(pump.isBasalInPump(profile(0.16)))
    }
}
