package info.nightscout.ui.defaultProfile

import info.nightscout.core.profile.ProfileSealed
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DefaultProfileTest : TestBaseWithProfile() {

    @Test
    fun profile() {
        val dp = DefaultProfile(dateUtil).profile(5, 5.1 / 0.3, 0.0, GlucoseUnit.MMOL)
        var p = ProfileSealed.Pure(dp!!)
        Assertions.assertEquals(0.150, p.getBasalTimeFromMidnight(0), 0.001)
        Assertions.assertEquals(15.0, p.getIcTimeFromMidnight(0), 0.001)
        Assertions.assertEquals(11.8, p.getIsfTimeFromMidnight(0), 0.001)

        p = ProfileSealed.Pure(DefaultProfile(dateUtil).profile(7, 10.0 / 0.4, 0.0, GlucoseUnit.MMOL)!!)
        Assertions.assertEquals(0.350, p.getBasalTimeFromMidnight(0), 0.001)
        Assertions.assertEquals(15.0, p.getIcTimeFromMidnight(0), 0.001)
        Assertions.assertEquals(6.8, p.getIsfTimeFromMidnight(0), 0.001)

        p = ProfileSealed.Pure(DefaultProfile(dateUtil).profile(12, 25.0 / 0.5, 0.0, GlucoseUnit.MMOL)!!)
        Assertions.assertEquals(0.80, p.getBasalTimeFromMidnight(0), 0.001)
        Assertions.assertEquals(10.0, p.getIcTimeFromMidnight(0), 0.001)
        Assertions.assertEquals(2.2, p.getIsfTimeFromMidnight(0), 0.001)
    }
}