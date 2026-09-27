package app.aaps.wear.comm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CgmAlarmWearTest {

    @Test
    fun patternFillsTheChosenTime() {
        listOf(3, 5, 7, 10).forEach { seconds ->
            listOf(true, false).forEach { urgent ->
                val pattern = CgmAlarmWear.pattern(seconds, urgent)
                assertThat(pattern.first()).isEqualTo(0L)
                val total = pattern.sum()
                assertThat(total).isAtLeast(seconds * 1000L)
                assertThat(total).isAtMost(seconds * 1000L + 400L)
                // Pulses (odd positions) are what the wearer feels.
                assertThat(pattern.filterIndexed { i, _ -> i % 2 == 1 }.all { it > 0 }).isTrue()
            }
        }
    }
}
