package app.aaps.ui.defaultProfile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class DefaultProfileTest : TestBaseWithProfile() {

    @Test
    fun profile() {
        val dp = DefaultProfile(dateUtil, profileUtil).profile(5, 5.1 / 0.3, 0.0, GlucoseUnit.MMOL)
        var p = ProfileSealed.Pure(dp!!, activePlugin)
        assertThat(p.getBasalTimeFromMidnight(0)).isWithin(0.001).of(0.150)
        assertThat(p.getIcTimeFromMidnight(0)).isWithin(0.001).of(15.0)
        assertThat(p.getIsfTimeFromMidnight(0)).isWithin(0.001).of(11.8)

        p = ProfileSealed.Pure(DefaultProfile(dateUtil, profileUtil).profile(7, 10.0 / 0.4, 0.0, GlucoseUnit.MMOL)!!, activePlugin)
        assertThat(p.getBasalTimeFromMidnight(0)).isWithin(0.001).of(0.350)
        assertThat(p.getIcTimeFromMidnight(0)).isWithin(0.001).of(15.0)
        assertThat(p.getIsfTimeFromMidnight(0)).isWithin(0.001).of(6.8)

        p = ProfileSealed.Pure(DefaultProfile(dateUtil, profileUtil).profile(12, 25.0 / 0.5, 0.0, GlucoseUnit.MMOL)!!, activePlugin)
        assertThat(p.getBasalTimeFromMidnight(0)).isWithin(0.001).of(0.80)
        assertThat(p.getIcTimeFromMidnight(0)).isWithin(0.001).of(10.0)
        assertThat(p.getIsfTimeFromMidnight(0)).isWithin(0.001).of(2.2)
    }
}
