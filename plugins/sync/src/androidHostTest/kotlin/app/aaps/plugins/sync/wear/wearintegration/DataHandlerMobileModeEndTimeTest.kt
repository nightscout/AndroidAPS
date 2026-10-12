package app.aaps.plugins.sync.wear.wearintegration

import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The end time the watch gets for the running mode. A real temporary mode ends at start plus
 * duration; a permanent mode and the open-ended sentinels have no end to show.
 */
class DataHandlerMobileModeEndTimeTest {

    private val start = 1_700_000_000_000L

    private fun mode(mode: RM.Mode, duration: Long) = RM(timestamp = start, mode = mode, duration = duration)

    @Test
    fun `a user suspend ends at start plus duration`() {
        val duration = T.mins(30).msecs()

        assertThat(mode(RM.Mode.SUSPENDED_BY_USER, duration).endTimeForWatch()).isEqualTo(start + duration)
    }

    @Test
    fun `a permanent mode has no end`() {
        assertThat(mode(RM.Mode.CLOSED_LOOP, 0L).endTimeForWatch()).isNull()
    }

    @Test
    fun `a pump suspend stored as Int MAX_VALUE minutes has no end`() {
        // LoopPlugin.runningModePreCheck writes this; shown as "35791394 h" before.
        assertThat(mode(RM.Mode.SUSPENDED_BY_PUMP, T.mins(Int.MAX_VALUE.toLong()).msecs()).endTimeForWatch()).isNull()
    }

    @Test
    fun `a Long MAX_VALUE duration has no end`() {
        // start + duration overflows here; the check must not reach that sum.
        assertThat(mode(RM.Mode.DISABLED_LOOP, Long.MAX_VALUE).endTimeForWatch()).isNull()
    }

    @Test
    fun `a duration just under the limit still has an end`() {
        val duration = T.days(30).msecs() - 1

        assertThat(mode(RM.Mode.DISCONNECTED_PUMP, duration).endTimeForWatch()).isEqualTo(start + duration)
    }
}
