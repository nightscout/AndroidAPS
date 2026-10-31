package app.aaps.plugins.source

import app.aaps.plugins.source.DexcomOnePlusSensorStartCorrection.Verdict
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The step-cap case checks that a free timestamp is never more than 60 steps away.
 */
class DexcomOnePlusSensorStartCorrectionTest {

    private val now = 1_700_000_000_000L
    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour

    @Test
    fun `an earlier time on the running sensor is accepted`() {
        assertThat(
            DexcomOnePlusSensorStartCorrection.validate(
                newStartMs = now - 8 * hour,
                currentStartMs = now - 2 * hour,
                nowMs = now,
            ),
        ).isEqualTo(Verdict.Accepted)
    }

    @Test
    fun `a later time is accepted too — the app may have been the early one`() {
        assertThat(
            DexcomOnePlusSensorStartCorrection.validate(
                newStartMs = now - 1 * hour,
                currentStartMs = now - 6 * hour,
                nowMs = now,
            ),
        ).isEqualTo(Verdict.Accepted)
    }

    @Test
    fun `the future is refused`() {
        assertThat(
            DexcomOnePlusSensorStartCorrection.validate(
                newStartMs = now + 1,
                currentStartMs = now - 2 * hour,
                nowMs = now,
            ),
        ).isEqualTo(Verdict.InFuture)
    }

    @Test
    fun `older than a whole sensor life is refused`() {
        assertThat(
            DexcomOnePlusSensorStartCorrection.validate(
                newStartMs = now - 11 * day,
                currentStartMs = now - 2 * hour,
                nowMs = now,
            ),
        ).isEqualTo(Verdict.TooOld)
    }

    @Test
    fun `the last hours of the grace window are still allowed`() {
        assertThat(
            DexcomOnePlusSensorStartCorrection.validate(
                newStartMs = now - (10 * day + 11 * hour),
                currentStartMs = now - 2 * hour,
                nowMs = now,
            ),
        ).isEqualTo(Verdict.Accepted)
    }

    @Test
    fun `with no session there is nothing to correct`() {
        assertThat(
            DexcomOnePlusSensorStartCorrection.validate(
                newStartMs = now - 2 * hour,
                currentStartMs = 0L,
                nowMs = now,
            ),
        ).isEqualTo(Verdict.NoSession)
    }

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

    @Test
    fun `the clean-up reaches back to whichever date is older`() {
        assertThat(DexcomOnePlusSensorStartCorrection.cleanupFrom(newStartMs = now - 8 * hour, currentStartMs = now - 2 * hour))
            .isEqualTo(now - 8 * hour)
        assertThat(DexcomOnePlusSensorStartCorrection.cleanupFrom(newStartMs = now - 1 * hour, currentStartMs = now - 6 * hour))
            .isEqualTo(now - 6 * hour)
    }
}
