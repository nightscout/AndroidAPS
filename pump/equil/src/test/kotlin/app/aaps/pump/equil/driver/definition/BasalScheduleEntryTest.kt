package app.aaps.pump.equil.driver.definition

import app.aaps.shared.tests.TestBase
import org.joda.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class BasalScheduleEntryTest : TestBase() {

    @Test
    fun `constructor should set rate and startTime`() {
        val entry = BasalScheduleEntry(1.5, Duration.standardHours(6))
        assertEquals(1.5, entry.rate, 0.001)
        assertEquals(Duration.standardHours(6), entry.startTime)
    }

    @Test
    fun `data class should support various rates`() {
        val entry1 = BasalScheduleEntry(0.0, Duration.ZERO)
        assertEquals(0.0, entry1.rate, 0.001)

        val entry2 = BasalScheduleEntry(0.05, Duration.standardMinutes(30))
        assertEquals(0.05, entry2.rate, 0.001)

        val entry3 = BasalScheduleEntry(5.0, Duration.standardHours(12))
        assertEquals(5.0, entry3.rate, 0.001)

        val entry4 = BasalScheduleEntry(25.0, Duration.standardHours(18))
        assertEquals(25.0, entry4.rate, 0.001)
    }

    @Test
    fun `data class should support various start times`() {
        val entry1 = BasalScheduleEntry(1.0, Duration.ZERO)
        assertEquals(Duration.ZERO, entry1.startTime)

        val entry2 = BasalScheduleEntry(1.0, Duration.standardMinutes(30))
        assertEquals(Duration.standardMinutes(30), entry2.startTime)

        val entry3 = BasalScheduleEntry(1.0, Duration.standardHours(12))
        assertEquals(Duration.standardHours(12), entry3.startTime)

        val entry4 = BasalScheduleEntry(1.0, Duration.standardSeconds(3600))
        assertEquals(Duration.standardSeconds(3600), entry4.startTime)
    }

    @Test
    fun `equals should work for identical entries`() {
        val entry1 = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val entry2 = BasalScheduleEntry(1.5, Duration.standardHours(6))
        assertEquals(entry1, entry2)
    }

    @Test
    fun `equals should fail for different rates`() {
        val entry1 = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val entry2 = BasalScheduleEntry(2.0, Duration.standardHours(6))
        assertNotEquals(entry1, entry2)
    }

    @Test
    fun `equals should fail for different start times`() {
        val entry1 = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val entry2 = BasalScheduleEntry(1.5, Duration.standardHours(12))
        assertNotEquals(entry1, entry2)
    }

    @Test
    fun `hashCode should be consistent with equals`() {
        val entry1 = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val entry2 = BasalScheduleEntry(1.5, Duration.standardHours(6))
        assertEquals(entry1.hashCode(), entry2.hashCode())
    }

    @Test
    fun `hashCode should differ for different entries`() {
        val entry1 = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val entry2 = BasalScheduleEntry(2.0, Duration.standardHours(6))
        assertNotEquals(entry1.hashCode(), entry2.hashCode())
    }

    @Test
    fun `copy should create independent copy`() {
        val original = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val copy = original.copy()
        assertEquals(original, copy)
        assertEquals(original.rate, copy.rate, 0.001)
        assertEquals(original.startTime, copy.startTime)
    }

    @Test
    fun `copy with rate change should work`() {
        val original = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val modified = original.copy(rate = 2.5)
        assertEquals(2.5, modified.rate, 0.001)
        assertEquals(original.startTime, modified.startTime)
    }

    @Test
    fun `copy with startTime change should work`() {
        val original = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val modified = original.copy(startTime = Duration.standardHours(12))
        assertEquals(original.rate, modified.rate, 0.001)
        assertEquals(Duration.standardHours(12), modified.startTime)
    }

    @Test
    fun `copy with both parameters changed should work`() {
        val original = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val modified = original.copy(rate = 2.5, startTime = Duration.standardHours(12))
        assertEquals(2.5, modified.rate, 0.001)
        assertEquals(Duration.standardHours(12), modified.startTime)
    }

    @Test
    fun `toString should include both properties`() {
        val entry = BasalScheduleEntry(1.5, Duration.standardHours(6))
        val str = entry.toString()
        assert(str.contains("1.5") || str.contains("rate"))
        assert(str.contains("PT6H") || str.contains("startTime") || str.contains("21600000"))
    }

    @Test
    fun `data class should handle precise decimal rates`() {
        val entry = BasalScheduleEntry(1.234567, Duration.standardMinutes(90))
        assertEquals(1.234567, entry.rate, 0.0000001)
    }

    @Test
    fun `data class should handle midnight start time`() {
        val entry = BasalScheduleEntry(1.0, Duration.ZERO)
        assertEquals(Duration.ZERO, entry.startTime)
        assertEquals(0, entry.startTime.standardSeconds)
    }

    @Test
    fun `data class should handle end of day start time`() {
        val entry = BasalScheduleEntry(1.0, Duration.standardHours(23).plus(Duration.standardMinutes(59)))
        assertEquals(Duration.standardHours(23).plus(Duration.standardMinutes(59)), entry.startTime)
    }
}
