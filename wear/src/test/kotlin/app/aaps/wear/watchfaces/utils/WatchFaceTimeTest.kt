package app.aaps.wear.watchfaces.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WatchFaceTime] — the java.time-backed replacement for the deprecated
 * android.text.format.Time used by watch faces. Verifies the epoch decomposition (including the
 * 0-based month kept for compatibility and the 12-hour conversion edge cases) and the
 * change-detection helpers that drive watch face redraws.
 */
class WatchFaceTimeTest {

    // 2021-01-01T00:00:00Z
    private val newYearUtcMidnight = 1609459200000L

    private fun utcTimeAt(millis: Long) = WatchFaceTime().apply {
        timezone = "UTC"
        set(millis)
    }

    @Test fun setDecomposesEpochInUtc() {
        val t = utcTimeAt(newYearUtcMidnight + 13 * 3600_000L + 45 * 60_000L + 30_000L) // 13:45:30
        assertThat(t.year).isEqualTo(2021)
        assertThat(t.month).isEqualTo(0)       // January, 0-based for compatibility
        assertThat(t.monthDay).isEqualTo(1)
        assertThat(t.hour).isEqualTo(13)
        assertThat(t.minute).isEqualTo(45)
        assertThat(t.second).isEqualTo(30)
        assertThat(t.millis).isEqualTo(newYearUtcMidnight + 13 * 3600_000L + 45 * 60_000L + 30_000L)
    }

    @Test fun hour12HandlesMidnightAndNoon() {
        assertThat(utcTimeAt(newYearUtcMidnight).hour12).isEqualTo(0)                       // 00:00 -> 0
        assertThat(utcTimeAt(newYearUtcMidnight + 12 * 3600_000L).hour12).isEqualTo(0)      // 12:00 -> 0
        assertThat(utcTimeAt(newYearUtcMidnight + 13 * 3600_000L).hour12).isEqualTo(1)      // 13:00 -> 1
        assertThat(utcTimeAt(newYearUtcMidnight + 23 * 3600_000L).hour12).isEqualTo(11)     // 23:00 -> 11
    }

    @Test fun invalidTimezoneFallsBackWithoutCrashing() {
        val t = WatchFaceTime().apply {
            timezone = "Not/AZone"
            set(newYearUtcMidnight)
        }
        // Decomposition still succeeded and the timezone was replaced by a valid resolved id
        assertThat(t.year).isGreaterThan(0)
        assertThat(t.timezone).isNotEqualTo("Not/AZone")
    }

    @Test fun setFromOtherCopiesAllFields() {
        val source = utcTimeAt(newYearUtcMidnight + 13 * 3600_000L + 45 * 60_000L + 30_000L)
        val copy = WatchFaceTime().apply { set(source) }
        assertThat(copy.year).isEqualTo(source.year)
        assertThat(copy.month).isEqualTo(source.month)
        assertThat(copy.monthDay).isEqualTo(source.monthDay)
        assertThat(copy.hour).isEqualTo(source.hour)
        assertThat(copy.minute).isEqualTo(source.minute)
        assertThat(copy.second).isEqualTo(source.second)
        assertThat(copy.timezone).isEqualTo(source.timezone)
        assertThat(copy.millis).isEqualTo(source.millis)
        assertThat(copy.hour12).isEqualTo(source.hour12)
    }

    @Test fun setFromNullOtherIsNoOp() {
        val t = utcTimeAt(newYearUtcMidnight)
        t.set(null)
        assertThat(t.millis).isEqualTo(newYearUtcMidnight)
    }

    @Test fun changeDetectionComparesComponents() {
        val base = utcTimeAt(newYearUtcMidnight + 13 * 3600_000L + 45 * 60_000L + 30_000L) // 13:45:30
        val sameMinuteDifferentSecond = utcTimeAt(newYearUtcMidnight + 13 * 3600_000L + 45 * 60_000L + 31_000L)
        val differentHour = utcTimeAt(newYearUtcMidnight + 14 * 3600_000L + 45 * 60_000L + 30_000L)

        assertThat(base.hasSecondChanged(sameMinuteDifferentSecond)).isTrue()
        assertThat(base.hasMinuteChanged(sameMinuteDifferentSecond)).isFalse()
        assertThat(base.hasHourChanged(sameMinuteDifferentSecond)).isFalse()

        assertThat(base.hasHourChanged(differentHour)).isTrue()
    }

    @Test fun changeDetectionTreatsNullAsChanged() {
        val t = utcTimeAt(newYearUtcMidnight)
        assertThat(t.hasHourChanged(null)).isTrue()
        assertThat(t.hasMinuteChanged(null)).isTrue()
        assertThat(t.hasSecondChanged(null)).isTrue()
    }

    @Test fun resetClearsComponents() {
        val t = utcTimeAt(newYearUtcMidnight + 13 * 3600_000L)
        t.reset()
        assertThat(t.year).isEqualTo(0)
        assertThat(t.hour).isEqualTo(0)
        assertThat(t.millis).isEqualTo(0)
    }
}
