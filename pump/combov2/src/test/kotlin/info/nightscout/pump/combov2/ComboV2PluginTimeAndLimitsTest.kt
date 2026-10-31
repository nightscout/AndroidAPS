package info.nightscout.pump.combov2

import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

class ComboV2PluginTimeAndLimitsTest {

    // millisUntilNextHour (#5261)

    @Test
    fun waitsUntilTheNextFullHour() {
        // The disconnect from the report that hung for 65 s.
        assertThat(millisUntilNextHour(DateTime(2026, 8, 10, 13, 58, 54, 391))).isEqualTo(65_609L)
        assertThat(millisUntilNextHour(DateTime(2026, 8, 10, 13, 30, 0, 0))).isEqualTo(30 * 60_000L)
        assertThat(millisUntilNextHour(DateTime(2026, 8, 10, 14, 0, 0, 0))).isEqualTo(60 * 60_000L)
    }

    /** delay(0) does not suspend and does not see a cancellation, so the wait must never be 0. */
    @Test
    fun neverWaitsZero() {
        assertThat(millisUntilNextHour(DateTime(2026, 8, 10, 13, 59, 59, 999))).isEqualTo(1L)
        for (minute in 0..59) {
            assertThat(millisUntilNextHour(DateTime(2026, 8, 10, 13, minute, 59, 999))).isAtLeast(1L)
        }
    }

    // capToMaxTempPercent (#5262)

    @Test
    fun capsAtMaxTempPercentOfTheBaseRate() {
        // The report: 2.3 U/h asked for, 0.43 U/h base, so the pump runs 500% = 2.15 U/h.
        assertThat(capToMaxTempPercent(rate = 2.3, baseRate = 0.43, maxTempPercent = 500)).isWithin(1e-9).of(2.15)
        assertThat(capToMaxTempPercent(rate = Double.MAX_VALUE, baseRate = 1.0, maxTempPercent = 500)).isWithin(1e-9).of(5.0)
    }

    @Test
    fun keepsARateBelowTheCap() {
        assertThat(capToMaxTempPercent(rate = 1.5, baseRate = 0.5, maxTempPercent = 500)).isEqualTo(1.5)
    }

    /** A cap of 0 would turn every temp basal into 0 U/h, so no cap while the base rate is unknown. */
    @Test
    fun noCapWhileTheBaseRateIsUnknown() {
        assertThat(capToMaxTempPercent(rate = 2.3, baseRate = 0.0, maxTempPercent = 500)).isEqualTo(2.3)
    }
}
