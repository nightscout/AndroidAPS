package info.nightscout.androidaps.data.defaultProfile

import info.nightscout.androidaps.Constants
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultProfileTest {

    @Test
    fun profile() {
        var p = DefaultProfile().profile(5.0, 5.1 / 0.3, 0.0, Constants.MGDL)
        assertEquals(0.150, p.getBasalTimeFromMidnight(0), 0.001)
        assertEquals(15.0, p.getIcTimeFromMidnight(0), 0.001)
        assertEquals(11.8, p.getIsfTimeFromMidnight(0), 0.001)

        p = DefaultProfile().profile(7.0, 10.0 / 0.4, 0.0, Constants.MGDL)
        assertEquals(0.350, p.getBasalTimeFromMidnight(0), 0.001)
        assertEquals(15.0, p.getIcTimeFromMidnight(0), 0.001)
        assertEquals(6.8, p.getIsfTimeFromMidnight(0), 0.001)

        p = DefaultProfile().profile(12.0, 25.0 / 0.5, 0.0, Constants.MGDL)
        assertEquals(0.80, p.getBasalTimeFromMidnight(0), 0.001)
        assertEquals(10.0, p.getIcTimeFromMidnight(0), 0.001)
        assertEquals(2.2, p.getIsfTimeFromMidnight(0), 0.001)
    }
}