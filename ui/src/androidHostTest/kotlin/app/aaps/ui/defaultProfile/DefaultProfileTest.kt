package app.aaps.ui.defaultProfile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.ui.compose.profileHelper.defaultProfile.DefaultProfile
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

    /**
     * The blocks used to be worked out from `timeAsSeconds` while parsing a JSON document, and are
     * built directly now. A block carries a duration rather than a start time, so an off-by-one in
     * the durations would shift every schedule after the first change - which the midnight-only
     * assertions above cannot see. These pin the value on each side of the changes.
     *
     * IC for age 1..5 is `250/tdd` rounded to 1.0, offset by
     * `[0, -4, -1, -2, -4, 0, -4]` at 00, 06, 09, 11, 14, 16 and 19 hours.
     */
    @Test
    fun `ic blocks change at the right hours`() {
        val p = ProfileSealed.Pure(DefaultProfile(dateUtil, profileUtil).profile(5, 5.1 / 0.3, 0.0, GlucoseUnit.MMOL)!!, activePlugin)
        val base = 15.0

        assertThat(p.getIcTimeFromMidnight(0)).isWithin(0.001).of(base)                       // 00:00
        assertThat(p.getIcTimeFromMidnight(6 * 3600 - 1)).isWithin(0.001).of(base)            // still the first block
        assertThat(p.getIcTimeFromMidnight(6 * 3600)).isWithin(0.001).of(base - 4.0)          // 06:00
        assertThat(p.getIcTimeFromMidnight(9 * 3600)).isWithin(0.001).of(base - 1.0)          // 09:00
        assertThat(p.getIcTimeFromMidnight(11 * 3600)).isWithin(0.001).of(base - 2.0)         // 11:00
        assertThat(p.getIcTimeFromMidnight(14 * 3600)).isWithin(0.001).of(base - 4.0)         // 14:00
        assertThat(p.getIcTimeFromMidnight(16 * 3600)).isWithin(0.001).of(base)               // 16:00
        assertThat(p.getIcTimeFromMidnight(19 * 3600)).isWithin(0.001).of(base - 4.0)         // 19:00
        assertThat(p.getIcTimeFromMidnight(24 * 3600 - 1)).isWithin(0.001).of(base - 4.0)     // last block runs to midnight
    }

    /** The basal schedule is 24 hourly blocks, so every hour must be readable. */
    @Test
    fun `basal is defined for every hour of the day`() {
        val p = ProfileSealed.Pure(DefaultProfile(dateUtil, profileUtil).profile(5, 5.1 / 0.3, 0.0, GlucoseUnit.MMOL)!!, activePlugin)

        for (hour in 0..23) {
            assertThat(p.getBasalTimeFromMidnight(hour * 3600)).isGreaterThan(0.0)
        }
    }

    /** A single target block covering the whole day, at 108 mg/dl expressed in the profile's units. */
    @Test
    fun `target covers the whole day in both units`() {
        val mmol = ProfileSealed.Pure(DefaultProfile(dateUtil, profileUtil).profile(5, 5.1 / 0.3, 0.0, GlucoseUnit.MMOL)!!, activePlugin)
        assertThat(mmol.getTargetLowMgdlTimeFromMidnight(0)).isWithin(0.01).of(108.0)
        assertThat(mmol.getTargetLowMgdlTimeFromMidnight(23 * 3600)).isWithin(0.01).of(108.0)

        val mgdl = ProfileSealed.Pure(DefaultProfile(dateUtil, profileUtil).profile(5, 5.1 / 0.3, 0.0, GlucoseUnit.MGDL)!!, activePlugin)
        assertThat(mgdl.getTargetLowMgdlTimeFromMidnight(0)).isWithin(0.01).of(108.0)
    }

    @Test
    fun `ages outside one to eighteen have no default profile`() {
        assertThat(DefaultProfile(dateUtil, profileUtil).profile(0, 20.0, 0.0, GlucoseUnit.MMOL)).isNull()
        assertThat(DefaultProfile(dateUtil, profileUtil).profile(19, 20.0, 0.0, GlucoseUnit.MMOL)).isNull()
    }
}
