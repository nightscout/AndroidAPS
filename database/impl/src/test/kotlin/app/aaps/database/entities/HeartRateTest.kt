package app.aaps.database.entities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.Test

class HeartRateTest {

    @Test
    fun contentEqualsTo_equals() {
        val hr1 = createHeartRate()
        assertTrue(hr1.contentEqualsTo(hr1))
        assertTrue(hr1.contentEqualsTo(hr1.copy()))
        assertTrue(hr1.contentEqualsTo(hr1.copy(id = 2, version = 2, dateCreated = 1L, referenceId = 4L)))
    }

    @Test
    fun contentEqualsTo_notEquals() {
        val hr1 = createHeartRate()
        assertFalse(hr1.contentEqualsTo(hr1.copy(duration = 60_001L)))
        assertFalse(hr1.contentEqualsTo(hr1.copy(timestamp = 2L)))
        assertFalse(hr1.contentEqualsTo(hr1.copy(duration = 60_001L)))
        assertFalse(hr1.contentEqualsTo(hr1.copy(beatsPerMinute = 100.0)))
        assertFalse(hr1.contentEqualsTo(hr1.copy(isValid = false)))
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
