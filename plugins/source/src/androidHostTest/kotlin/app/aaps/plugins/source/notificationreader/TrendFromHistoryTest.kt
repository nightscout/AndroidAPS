package app.aaps.plugins.source.notificationreader

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * A notification gives the reader only the number, so it works out the arrow from the readings stored
 * before it (#5648).
 */
class TrendFromHistoryTest {

    private val now = 1_000_000_000L

    private fun reading(minutesAgo: Long, value: Double) =
        GV(timestamp = now - T.mins(minutesAgo).msecs(), value = value, raw = null, noise = null, trendArrow = TrendArrow.NONE, sourceSensor = SourceSensor.UNKNOWN)

    @Test
    fun `a 5 minute sensor takes the slope against the reading before`() {
        // 15 mg/dL in 5 minutes is 3 mg/dL a minute
        assertThat(TrendFromHistory.arrow(now, 100.0, listOf(reading(5, 85.0)))).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `a falling reading gets a falling arrow`() {
        // -20 mg/dL in 5 minutes is -4 mg/dL a minute
        assertThat(TrendFromHistory.arrow(now, 100.0, listOf(reading(5, 120.0)))).isEqualTo(TrendArrow.DOUBLE_DOWN)
    }

    /** Against the reading of 1 minute before, one step of 0.1 mmol/L would already be a 45 degree arrow. */
    @Test
    fun `a 1 minute sensor takes the slope over at least 4 minutes`() {
        // 6.5 mmol/L (117 mg/dL) for 5 minutes, then one step to 6.6 mmol/L (119 mg/dL)
        val history = listOf(reading(1, 117.0), reading(2, 117.0), reading(3, 117.0), reading(4, 117.0), reading(5, 117.0))

        assertThat(TrendFromHistory.arrow(now, 119.0, history)).isEqualTo(TrendArrow.FLAT)
    }

    @Test
    fun `the newest reading in the window is used, in any order`() {
        // Against the reading of 10 or of 12 minutes before it would be FLAT
        val history = listOf(reading(10, 95.0), reading(5, 85.0), reading(12, 90.0))

        assertThat(TrendFromHistory.arrow(now, 100.0, history)).isEqualTo(TrendArrow.SINGLE_UP)
    }

    @Test
    fun `without a reading from 4 to 15 minutes before there is no arrow`() {
        assertThat(TrendFromHistory.arrow(now, 100.0, emptyList())).isEqualTo(TrendArrow.NONE)
        // Too old to say anything about the trend now
        assertThat(TrendFromHistory.arrow(now, 100.0, listOf(reading(16, 50.0)))).isEqualTo(TrendArrow.NONE)
        // Too new for a slope over about 5 minutes
        assertThat(TrendFromHistory.arrow(now, 100.0, listOf(reading(3, 50.0)))).isEqualTo(TrendArrow.NONE)
    }
}
