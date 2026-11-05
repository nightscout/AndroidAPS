package app.aaps.wear.alarm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CgmAlarmWatchTest {

    @Test
    fun patternFillsTheChosenTime() {
        listOf(3, 5, 7, 10).forEach { seconds ->
            listOf(true, false).forEach { urgent ->
                val pattern = CgmAlarmWatch.pattern(seconds, urgent)
                assertThat(pattern.first()).isEqualTo(0L)
                val total = pattern.sum()
                assertThat(total).isAtLeast(seconds * 1000L)
                assertThat(total).isAtMost(seconds * 1000L + 400L)
                // Pulses (odd positions) are what the wearer feels.
                assertThat(pattern.filterIndexed { i, _ -> i % 2 == 1 }.all { it > 0 }).isTrue()
            }
        }
    }

    @Test
    fun noVibrationTimeGivesOnlyTheStart() {
        assertThat(CgmAlarmWatch.pattern(0, urgent = true).toList()).containsExactly(0L)
    }

    @Test
    fun pulsesAtFullStrength() {
        val timings = CgmAlarmWatch.pattern(5, urgent = true)
        val amplitudes = CgmAlarmWatch.amplitudes(timings)
        assertThat(amplitudes.size).isEqualTo(timings.size)
        amplitudes.forEachIndexed { i, a -> assertThat(a).isEqualTo(if (i % 2 == 1) 255 else 0) }
    }

    @Test
    fun eachAlarmHasItsOwnNotification() {
        assertThat(CgmAlarmWatch.notificationId("urgent_low")).isNotEqualTo(CgmAlarmWatch.notificationId("signal_loss"))
        assertThat(CgmAlarmWatch.notificationId("urgent_low")).isEqualTo(CgmAlarmWatch.notificationId("urgent_low"))
    }
}
