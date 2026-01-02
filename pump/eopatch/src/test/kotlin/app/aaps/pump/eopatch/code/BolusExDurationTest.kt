package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class BolusExDurationTest {

    @Test
    fun `ofRaw should return correct duration for valid minute values`() {
        assertThat(BolusExDuration.ofRaw(0)).isEqualTo(BolusExDuration.OFF)
        assertThat(BolusExDuration.ofRaw(30)).isEqualTo(BolusExDuration.MINUTE_30)
        assertThat(BolusExDuration.ofRaw(60)).isEqualTo(BolusExDuration.MINUTE_60)
        assertThat(BolusExDuration.ofRaw(120)).isEqualTo(BolusExDuration.MINUTE_120)
        assertThat(BolusExDuration.ofRaw(480)).isEqualTo(BolusExDuration.MINUTE_480)
    }

    @Test
    fun `ofRaw should return OFF for invalid values`() {
        assertThat(BolusExDuration.ofRaw(-1)).isEqualTo(BolusExDuration.OFF)
        assertThat(BolusExDuration.ofRaw(45)).isEqualTo(BolusExDuration.OFF)
        assertThat(BolusExDuration.ofRaw(999)).isEqualTo(BolusExDuration.OFF)
    }

    @Test
    fun `milli should convert minutes to milliseconds correctly`() {
        assertThat(BolusExDuration.MINUTE_30.milli()).isEqualTo(TimeUnit.MINUTES.toMillis(30))
        assertThat(BolusExDuration.MINUTE_60.milli()).isEqualTo(TimeUnit.MINUTES.toMillis(60))
        assertThat(BolusExDuration.MINUTE_120.milli()).isEqualTo(TimeUnit.MINUTES.toMillis(120))
        assertThat(BolusExDuration.OFF.milli()).isEqualTo(0)
    }

    @Test
    fun `hour should be calculated correctly from minutes`() {
        assertThat(BolusExDuration.MINUTE_30.hour).isWithin(0.01f).of(0.5f)
        assertThat(BolusExDuration.MINUTE_60.hour).isWithin(0.01f).of(1.0f)
        assertThat(BolusExDuration.MINUTE_90.hour).isWithin(0.01f).of(1.5f)
        assertThat(BolusExDuration.MINUTE_120.hour).isWithin(0.01f).of(2.0f)
        assertThat(BolusExDuration.MINUTE_480.hour).isWithin(0.01f).of(8.0f)
        assertThat(BolusExDuration.OFF.hour).isWithin(0.01f).of(0f)
    }

    @Test
    fun `index should be sequential`() {
        assertThat(BolusExDuration.OFF.index).isEqualTo(0)
        assertThat(BolusExDuration.MINUTE_30.index).isEqualTo(1)
        assertThat(BolusExDuration.MINUTE_60.index).isEqualTo(2)
        assertThat(BolusExDuration.MINUTE_480.index).isEqualTo(16)
    }

    @Test
    fun `minute should match enum name`() {
        assertThat(BolusExDuration.MINUTE_30.minute).isEqualTo(30)
        assertThat(BolusExDuration.MINUTE_60.minute).isEqualTo(60)
        assertThat(BolusExDuration.MINUTE_90.minute).isEqualTo(90)
        assertThat(BolusExDuration.MINUTE_120.minute).isEqualTo(120)
        assertThat(BolusExDuration.MINUTE_480.minute).isEqualTo(480)
    }

    @Test
    fun `all durations should be in 30 minute increments`() {
        for (duration in BolusExDuration.entries) {
            if (duration != BolusExDuration.OFF) {
                assertThat(duration.minute % 30).isEqualTo(0)
            }
        }
    }

    @Test
    fun `all enum values should be accessible`() {
        assertThat(BolusExDuration.entries).hasSize(17)
        assertThat(BolusExDuration.entries).contains(BolusExDuration.OFF)
        assertThat(BolusExDuration.entries).contains(BolusExDuration.MINUTE_30)
        assertThat(BolusExDuration.entries).contains(BolusExDuration.MINUTE_480)
    }

    @Test
    fun `durations should be in ascending order`() {
        val entries = BolusExDuration.entries
        for (i in 0 until entries.size - 1) {
            assertThat(entries[i].minute).isLessThan(entries[i + 1].minute)
        }
    }
}
