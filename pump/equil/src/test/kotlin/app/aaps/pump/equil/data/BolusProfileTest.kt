package app.aaps.pump.equil.data

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BolusProfileTest : TestBase() {

    private lateinit var bolusProfile: BolusProfile

    @BeforeEach
    fun setUp() {
        bolusProfile = BolusProfile()
    }

    @Test
    fun `default values should be initialized correctly`() {
        assertEquals(0L, bolusProfile.timestamp)
        assertFalse(bolusProfile.stop)
        assertEquals(0.0, bolusProfile.insulin, 0.001)
    }

    @Test
    fun `timestamp should be settable`() {
        val timestamp = System.currentTimeMillis()
        bolusProfile.timestamp = timestamp
        assertEquals(timestamp, bolusProfile.timestamp)
    }

    @Test
    fun `timestamp should handle various values`() {
        bolusProfile.timestamp = 0L
        assertEquals(0L, bolusProfile.timestamp)

        bolusProfile.timestamp = Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, bolusProfile.timestamp)

        bolusProfile.timestamp = 1234567890L
        assertEquals(1234567890L, bolusProfile.timestamp)
    }

    @Test
    fun `stop flag should be settable`() {
        assertFalse(bolusProfile.stop)

        bolusProfile.stop = true
        assertTrue(bolusProfile.stop)

        bolusProfile.stop = false
        assertFalse(bolusProfile.stop)
    }

    @Test
    fun `insulin should be settable`() {
        bolusProfile.insulin = 5.5
        assertEquals(5.5, bolusProfile.insulin, 0.001)
    }

    @Test
    fun `insulin should handle various values`() {
        bolusProfile.insulin = 0.0
        assertEquals(0.0, bolusProfile.insulin, 0.001)

        bolusProfile.insulin = 0.05
        assertEquals(0.05, bolusProfile.insulin, 0.001)

        bolusProfile.insulin = 10.0
        assertEquals(10.0, bolusProfile.insulin, 0.001)

        bolusProfile.insulin = 25.5
        assertEquals(25.5, bolusProfile.insulin, 0.001)
    }

    @Test
    fun `insulin should handle precise decimal values`() {
        bolusProfile.insulin = 3.14159
        assertEquals(3.14159, bolusProfile.insulin, 0.00001)

        bolusProfile.insulin = 0.123456789
        assertEquals(0.123456789, bolusProfile.insulin, 0.000000001)
    }

    @Test
    fun `all properties should be mutable independently`() {
        val timestamp = 1234567890L
        val insulin = 7.5
        val stop = true

        bolusProfile.timestamp = timestamp
        bolusProfile.insulin = insulin
        bolusProfile.stop = stop

        assertEquals(timestamp, bolusProfile.timestamp)
        assertEquals(insulin, bolusProfile.insulin, 0.001)
        assertEquals(stop, bolusProfile.stop)
    }

    @Test
    fun `multiple instances should be independent`() {
        val profile1 = BolusProfile()
        val profile2 = BolusProfile()

        profile1.timestamp = 1000L
        profile1.insulin = 5.0
        profile1.stop = true

        profile2.timestamp = 2000L
        profile2.insulin = 10.0
        profile2.stop = false

        assertEquals(1000L, profile1.timestamp)
        assertEquals(5.0, profile1.insulin, 0.001)
        assertTrue(profile1.stop)

        assertEquals(2000L, profile2.timestamp)
        assertEquals(10.0, profile2.insulin, 0.001)
        assertFalse(profile2.stop)
    }

    @Test
    fun `properties should be overwritable`() {
        bolusProfile.timestamp = 1000L
        bolusProfile.timestamp = 2000L
        assertEquals(2000L, bolusProfile.timestamp)

        bolusProfile.insulin = 5.0
        bolusProfile.insulin = 7.5
        assertEquals(7.5, bolusProfile.insulin, 0.001)

        bolusProfile.stop = true
        bolusProfile.stop = false
        assertFalse(bolusProfile.stop)
    }
}
