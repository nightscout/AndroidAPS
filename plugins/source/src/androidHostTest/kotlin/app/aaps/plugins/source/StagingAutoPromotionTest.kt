package app.aaps.plugins.source

import app.aaps.plugins.source.StagingAutoPromotion.Decision
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class StagingAutoPromotionTest {

    private val minute = 60L * 1000L
    private val hour = 60L * minute
    private val now = 1_000L * hour
    private val start = now - 11L * hour

    /** One reading every 5 min over the last 30 min, the newest one 1 min ago. */
    private fun everyFiveMinutes(mgdl: Double = 120.0) =
        (0 until 6).map { i -> (now - minute - i * 5L * minute) to mgdl }

    private fun decide(
        enabled: Boolean = true,
        delayHours: Int = 10,
        startMs: Long = start,
        readings: List<Pair<Long, Double>> = everyFiveMinutes(),
        minRecent: Int = 4,
    ) = StagingAutoPromotion.decide(enabled, delayHours, startMs, readings, minRecent, now)

    @Test
    fun `promotes when every check passes`() {
        assertThat(decide()).isEqualTo(Decision.PROMOTE)
    }

    @Test
    fun `does nothing when the setting is off`() {
        assertThat(decide(enabled = false)).isEqualTo(Decision.DISABLED)
    }

    @Test
    fun `does nothing without a start time`() {
        assertThat(decide(startMs = 0L)).isEqualTo(Decision.NO_START)
    }

    @Test
    fun `waits until the set hours have passed`() {
        assertThat(decide(delayHours = 12)).isEqualTo(Decision.TOO_EARLY)
        assertThat(decide(startMs = now - 10L * hour)).isEqualTo(Decision.PROMOTE)
        assertThat(decide(startMs = now - 10L * hour + 1)).isEqualTo(Decision.TOO_EARLY)
    }

    @Test
    fun `needs enough recent readings`() {
        assertThat(decide(readings = everyFiveMinutes().take(3))).isEqualTo(Decision.TOO_FEW_RECENT)
        assertThat(decide(readings = emptyList())).isEqualTo(Decision.TOO_FEW_RECENT)
    }

    @Test
    fun `old readings do not count as recent`() {
        val old = (0 until 10).map { i -> (now - 40L * minute - i * minute) to 120.0 }
        assertThat(decide(readings = old)).isEqualTo(Decision.TOO_FEW_RECENT)
    }

    @Test
    fun `refuses when the newest reading is too old`() {
        val readings = (0 until 4).map { i -> (now - 16L * minute - i * minute) to 120.0 }
        assertThat(decide(readings = readings)).isEqualTo(Decision.STALE)
    }

    @Test
    fun `refuses when a recent reading is at the sensor limit`() {
        assertThat(decide(readings = everyFiveMinutes() + ((now - 2L * minute) to 39.0))).isEqualTo(Decision.RAILED)
        assertThat(decide(readings = everyFiveMinutes() + ((now - 2L * minute) to 400.0))).isEqualTo(Decision.RAILED)
    }

    @Test
    fun `a reading in the future is ignored`() {
        val readings = everyFiveMinutes().take(3) + ((now + 5L * minute) to 120.0)
        assertThat(decide(readings = readings)).isEqualTo(Decision.TOO_FEW_RECENT)
    }
}
