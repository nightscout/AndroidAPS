package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.Scene
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ActiveSceneStateTest {

    private val scene = Scene(id = "test", name = "Test")
    private val priorState = ActiveSceneState.PriorState()

    private fun timedState(activatedAt: Long, durationMs: Long) = ActiveSceneState(
        scene = scene,
        activatedAt = activatedAt,
        durationMs = durationMs,
        priorState = priorState
    )

    private fun indefiniteState(activatedAt: Long) = ActiveSceneState(
        scene = scene,
        activatedAt = activatedAt,
        durationMs = 0,
        priorState = priorState
    )

    @Test
    fun endsAt_withDuration_returnsCorrectTime() {
        val state = timedState(activatedAt = 1000L, durationMs = 5000L)
        assertThat(state.endsAt).isEqualTo(6000L)
    }

    @Test
    fun endsAt_indefinite_returnsNull() {
        val state = indefiniteState(activatedAt = 1000L)
        assertThat(state.endsAt).isNull()
    }

    @Test
    fun isExpired_beforeEnd_returnsFalse() {
        val state = timedState(activatedAt = 1000L, durationMs = 5000L)
        // now=3000, ends at 6000 -> not expired
        assertThat(state.isExpired(3000L)).isFalse()
    }

    @Test
    fun isExpired_afterEnd_returnsTrue() {
        val state = timedState(activatedAt = 1000L, durationMs = 5000L)
        // now=7000, ends at 6000 -> expired
        assertThat(state.isExpired(7000L)).isTrue()
    }

    @Test
    fun isExpired_indefinite_returnsFalse() {
        val state = indefiniteState(activatedAt = 1000L)
        assertThat(state.isExpired(999_999_999L)).isFalse()
    }

    @Test
    fun remainingMs_halfwayThrough_returnsHalf() {
        val state = timedState(activatedAt = 0L, durationMs = 10_000L)
        // now=5000, ends at 10000 -> remaining = 5000
        assertThat(state.remainingMs(5000L)).isEqualTo(5000L)
    }

    @Test
    fun remainingMs_expired_returnsZero() {
        val state = timedState(activatedAt = 0L, durationMs = 10_000L)
        // now=15000, ends at 10000 -> remaining clamped to 0
        assertThat(state.remainingMs(15_000L)).isEqualTo(0L)
    }

    @Test
    fun remainingMs_indefinite_returnsNull() {
        val state = indefiniteState(activatedAt = 0L)
        assertThat(state.remainingMs(5000L)).isNull()
    }
}
