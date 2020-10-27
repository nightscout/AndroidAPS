package info.nightscout.androidaps.data.defaultProfile

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultProfileTest : TestBase() {

    val injector = HasAndroidInjector { AndroidInjector { } }

    @Test
    fun profile() {
        var p = DefaultProfile(injector).profile(5.0, 5.1 / 0.3, 0.0, Constants.MMOL)
        assertEquals(0.150, p!!.getBasalTimeFromMidnight(0), 0.001)
        assertEquals(15.0, p.getIcTimeFromMidnight(0), 0.001)
        assertEquals(11.8, p.getIsfTimeFromMidnight(0), 0.001)

        p = DefaultProfile(injector).profile(7.0, 10.0 / 0.4, 0.0, Constants.MMOL)
        assertEquals(0.350, p!!.getBasalTimeFromMidnight(0), 0.001)
        assertEquals(15.0, p.getIcTimeFromMidnight(0), 0.001)
        assertEquals(6.8, p.getIsfTimeFromMidnight(0), 0.001)

        p = DefaultProfile(injector).profile(12.0, 25.0 / 0.5, 0.0, Constants.MMOL)
        assertEquals(0.80, p!!.getBasalTimeFromMidnight(0), 0.001)
        assertEquals(10.0, p.getIcTimeFromMidnight(0), 0.001)
        assertEquals(2.2, p.getIsfTimeFromMidnight(0), 0.001)
    }
}