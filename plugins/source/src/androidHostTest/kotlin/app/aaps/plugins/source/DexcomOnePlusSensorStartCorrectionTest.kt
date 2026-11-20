package app.aaps.plugins.source

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The step-cap case checks that a free timestamp is never more than 60 steps away.
 */
class DexcomOnePlusSensorStartCorrectionTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `a moment another sensor change already holds is stepped over`() {
        val taken = setOf(now, now + 1_000L)

        assertThat(DexcomOnePlusSensorStartCorrection.freeTimestamp(now, taken)).isEqualTo(now + 2_000L)
        assertThat(DexcomOnePlusSensorStartCorrection.freeTimestamp(now, emptySet())).isEqualTo(now)
        assertThat(DexcomOnePlusSensorStartCorrection.freeTimestamp(now, setOf(now)) - now).isAtMost(1_000L)
    }

    @Test
    fun `the walk stops after sixty seconds`() {
        val taken = (0..60).map { now + it * 1_000L }.toSet()

        assertThat(DexcomOnePlusSensorStartCorrection.freeTimestamp(now, taken) - now).isEqualTo(60_000L)
    }
}
