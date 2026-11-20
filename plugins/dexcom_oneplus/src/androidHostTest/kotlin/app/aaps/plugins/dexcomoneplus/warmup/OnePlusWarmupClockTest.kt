package app.aaps.plugins.dexcomoneplus.warmup

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusWarmupClockTest {

    @Test
    fun `resolveRemainingMs prefers remainingMs`() {
        val state = OnePlusWarmupState(
            phase = OnePlusWarmupState.Phase.WARMING,
            remainingMs = 12_000L,
            endsAtEpochMs = 9_999_999L,
        )
        assertThat(OnePlusWarmupClock.resolveRemainingMs(state, nowMs = 0L)).isEqualTo(12_000L)
    }

    @Test
    fun `resolveRemainingMs uses endsAt when remaining null`() {
        val state = OnePlusWarmupState(
            phase = OnePlusWarmupState.Phase.WARMING,
            remainingMs = null,
            endsAtEpochMs = 50_000L,
        )
        assertThat(OnePlusWarmupClock.resolveRemainingMs(state, nowMs = 20_000L)).isEqualTo(30_000L)
    }

    @Test
    fun `resolveRemainingMs returns null when no clock`() {
        val state = OnePlusWarmupState(phase = OnePlusWarmupState.Phase.IDLE)
        assertThat(OnePlusWarmupClock.resolveRemainingMs(state, nowMs = 1L)).isNull()
    }

    @Test
    fun `warmingFromStart uses default 30 min and READY when elapsed`() {
        val start = 1_000_000L
        val mid = OnePlusWarmupClock.warmingFromStart(
            startEpochMs = start,
            nowMs = start + 10 * 60 * 1000L,
        )
        assertThat(mid.phase).isEqualTo(OnePlusWarmupState.Phase.WARMING)
        assertThat(mid.remainingMs).isEqualTo(20 * 60 * 1000L)
        assertThat(mid.endsAtEpochMs).isEqualTo(start + OnePlusWarmupClock.DEFAULT_WARMUP_MS)

        val done = OnePlusWarmupClock.warmingFromStart(
            startEpochMs = start,
            nowMs = start + OnePlusWarmupClock.DEFAULT_WARMUP_MS + 1L,
        )
        assertThat(done.phase).isEqualTo(OnePlusWarmupState.Phase.READY)
        assertThat(done.remainingMs).isEqualTo(0L)
    }
}
