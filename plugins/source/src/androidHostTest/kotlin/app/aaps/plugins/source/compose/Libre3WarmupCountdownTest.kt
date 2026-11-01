package app.aaps.plugins.source.compose

import app.aaps.plugins.libre3.Libre3WarmupState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What the countdown shows, and where the number comes from.
 *
 * The driver state is still what each case builds, then mapped, so the real driver phases stay
 * covered.
 */
class Libre3WarmupCountdownTest {

    private val now = 1_777_216_508_000L

    @Test
    fun `what the sensor said is used first`() {
        val state = Libre3WarmupState(
            phase = Libre3WarmupState.Phase.WARMING,
            remainingMs = 900_000L,
            endsAtEpochMs = now + 60_000L,
        )

        assertThat(Libre3WarmupCountdown.remainingMs(state.toCgmWarmupInfo(), now)).isEqualTo(900_000L)
    }

    @Test
    fun `an end time is used when no time left was given`() {
        val state = Libre3WarmupState(phase = Libre3WarmupState.Phase.WARMING, endsAtEpochMs = now + 300_000L)

        assertThat(Libre3WarmupCountdown.remainingMs(state.toCgmWarmupInfo(), now)).isEqualTo(300_000L)
    }

    @Test
    fun `nothing known shows nothing, it never guesses`() {
        val state = Libre3WarmupState(phase = Libre3WarmupState.Phase.WARMING)

        assertThat(Libre3WarmupCountdown.remainingMs(state.toCgmWarmupInfo(), now)).isNull()
    }

    @Test
    fun `a time that has passed shows zero, never a negative number`() {
        val state = Libre3WarmupState(phase = Libre3WarmupState.Phase.WARMING, endsAtEpochMs = now - 60_000L)

        assertThat(Libre3WarmupCountdown.remainingMs(state.toCgmWarmupInfo(), now)).isEqualTo(0L)
    }

    @Test
    fun `the countdown is written as minutes and seconds`() {
        assertThat(Libre3WarmupCountdown.format(1_785_000L)).isEqualTo("29:45")
        assertThat(Libre3WarmupCountdown.format(0L)).isEqualTo("00:00")
        assertThat(Libre3WarmupCountdown.format(65_000L)).isEqualTo("01:05")
    }

    @Test
    fun `a running sensor counts as finished`() {
        val state = Libre3WarmupState(phase = Libre3WarmupState.Phase.READY)

        assertThat(Libre3WarmupCountdown.isFinished(state.toCgmWarmupInfo(), now)).isTrue()
    }

    @Test
    fun `a sensor with time left is not finished`() {
        val state = Libre3WarmupState(phase = Libre3WarmupState.Phase.WARMING, remainingMs = 60_000L)

        assertThat(Libre3WarmupCountdown.isFinished(state.toCgmWarmupInfo(), now)).isFalse()
    }

    @Test
    fun `a sensor whose countdown reached zero is finished`() {
        val state = Libre3WarmupState(phase = Libre3WarmupState.Phase.WARMING, remainingMs = 0L)

        assertThat(Libre3WarmupCountdown.isFinished(state.toCgmWarmupInfo(), now)).isTrue()
    }
}
