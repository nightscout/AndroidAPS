package app.aaps.database.entities

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HeartRateTest {

    @Test
    fun contentEqualsTo_equals() {
        val hr1 = createHeartRate()
        assertThat(hr1.contentEqualsTo(hr1)).isTrue()
        assertThat(hr1.contentEqualsTo(hr1.copy())).isTrue()
        assertThat(hr1.contentEqualsTo(hr1.copy(id = 2, version = 2, dateCreated = 1L, referenceId = 4L))).isTrue()
    }

    @Test
    fun contentEqualsTo_notEquals() {
        val hr1 = createHeartRate()
        assertThat(hr1.contentEqualsTo(hr1.copy(duration = 60_001L))).isFalse()
        assertThat(hr1.contentEqualsTo(hr1.copy(timestamp = 2L))).isFalse()
        assertThat(hr1.contentEqualsTo(hr1.copy(duration = 60_001L))).isFalse()
        assertThat(hr1.contentEqualsTo(hr1.copy(beatsPerMinute = 100.0))).isFalse()
        assertThat(hr1.contentEqualsTo(hr1.copy(isValid = false))).isFalse()
    }

    fun HeartRate.contentEqualsTo(other: HeartRate): Boolean {
        return this === other || (
            duration == other.duration &&
                timestamp == other.timestamp &&
                beatsPerMinute == other.beatsPerMinute &&
                isValid == other.isValid)
    }

    companion object {

        fun createHeartRate(timestamp: Long? = null, beatsPerMinute: Double = 80.0) =
            HeartRate(
                timestamp = timestamp ?: System.currentTimeMillis(),
                duration = 60_0000L,
                beatsPerMinute = beatsPerMinute,
                device = "T",
            )
    }
}
