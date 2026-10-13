package app.aaps.pump.equil.driver.definition

import app.aaps.core.interfaces.profile.Profile
import app.aaps.shared.tests.TestBase
import org.joda.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class BasalScheduleTest : TestBase() {

    @Test
    fun `constructor should require non-empty entries`() {
        assertThrows<IllegalArgumentException> {
            BasalSchedule(emptyList())
        }
    }

    @Test
    fun `constructor should require first entry to start at zero`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.standardHours(1))
        )
        assertThrows<IllegalArgumentException> {
            BasalSchedule(entries)
        }
    }

    @Test
    fun `constructor should accept valid entries`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6)),
            BasalScheduleEntry(0.8, Duration.standardHours(12))
        )
        val schedule = BasalSchedule(entries)
        assertEquals(entries, schedule.getEntries())
    }

    @Test
    fun `rateAt should return correct rate for exact time match`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6)),
            BasalScheduleEntry(0.8, Duration.standardHours(12))
        )
        val schedule = BasalSchedule(entries)

        assertEquals(1.0, schedule.rateAt(Duration.ZERO), 0.001)
        assertEquals(1.5, schedule.rateAt(Duration.standardHours(6)), 0.001)
        assertEquals(0.8, schedule.rateAt(Duration.standardHours(12)), 0.001)
    }

    @Test
    fun `rateAt should return correct rate for time between entries`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6)),
            BasalScheduleEntry(0.8, Duration.standardHours(12))
        )
        val schedule = BasalSchedule(entries)

        // Time between 0 and 6 hours should return rate from entry at 0
        assertEquals(1.0, schedule.rateAt(Duration.standardHours(3)), 0.001)

        // Time between 6 and 12 hours should return rate from entry at 6
        assertEquals(1.5, schedule.rateAt(Duration.standardHours(9)), 0.001)

        // Time after 12 hours should return rate from entry at 12
        assertEquals(0.8, schedule.rateAt(Duration.standardHours(18)), 0.001)
    }

    @Test
    fun `rateAt should handle end of day correctly`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(0.5, Duration.standardHours(22))
        )
        val schedule = BasalSchedule(entries)

        // Just before end of day
        assertEquals(0.5, schedule.rateAt(Duration.standardHours(23)), 0.001)
        assertEquals(0.5, schedule.rateAt(Duration.standardMinutes(23 * 60 + 59)), 0.001)
    }

    @Test
    fun `rateAt should handle minutes correctly`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardMinutes(30)),
            BasalScheduleEntry(2.0, Duration.standardMinutes(90))
        )
        val schedule = BasalSchedule(entries)

        assertEquals(1.0, schedule.rateAt(Duration.standardMinutes(15)), 0.001)
        assertEquals(1.5, schedule.rateAt(Duration.standardMinutes(30)), 0.001)
        assertEquals(1.5, schedule.rateAt(Duration.standardMinutes(60)), 0.001)
        assertEquals(2.0, schedule.rateAt(Duration.standardMinutes(90)), 0.001)
        assertEquals(2.0, schedule.rateAt(Duration.standardMinutes(120)), 0.001)
    }

    @Test
    fun `rateAt should throw exception for negative duration`() {
        val entries = listOf(BasalScheduleEntry(1.0, Duration.ZERO))
        val schedule = BasalSchedule(entries)

        assertThrows<IllegalArgumentException> {
            schedule.rateAt(Duration.standardHours(-1))
        }
    }

    @Test
    fun `rateAt should throw exception for duration longer than 24 hours`() {
        val entries = listOf(BasalScheduleEntry(1.0, Duration.ZERO))
        val schedule = BasalSchedule(entries)

        assertThrows<IllegalArgumentException> {
            schedule.rateAt(Duration.standardHours(25))
        }
    }

    @Test
    fun `rateAt should accept exactly 24 hours`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(0.8, Duration.standardHours(12))
        )
        val schedule = BasalSchedule(entries)

        // Should not throw - exactly 24 hours should be valid
        assertEquals(0.8, schedule.rateAt(Duration.standardHours(24)), 0.001)
    }

    @Test
    fun `getEntries should return copy of entries`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6))
        )
        val schedule = BasalSchedule(entries)
        val retrievedEntries = schedule.getEntries()

        assertEquals(entries, retrievedEntries)
    }

    @Test
    fun `equals should return true for schedules with same entries`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6))
        )
        val schedule1 = BasalSchedule(entries)
        val schedule2 = BasalSchedule(entries)

        assertEquals(schedule1, schedule2)
    }

    @Test
    fun `equals should return false for schedules with different entries`() {
        val entries1 = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6))
        )
        val entries2 = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(2.0, Duration.standardHours(6))
        )
        val schedule1 = BasalSchedule(entries1)
        val schedule2 = BasalSchedule(entries2)

        assertNotEquals(schedule1, schedule2)
    }

    @Test
    fun `hashCode should be consistent with equals`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6))
        )
        val schedule1 = BasalSchedule(entries)
        val schedule2 = BasalSchedule(entries)

        assertEquals(schedule1.hashCode(), schedule2.hashCode())
    }

    @Test
    fun `mapProfileToBasalSchedule should create schedule with 24 entries`() {
        val profile = mock(Profile::class.java)

        // Mock profile to return different basal rates for each hour
        for (i in 0..23) {
            whenever(profile.getBasalTimeFromMidnight(i * 60 * 60)).thenReturn(1.0 + i * 0.1)
        }

        val schedule = BasalSchedule.mapProfileToBasalSchedule(profile)
        assertEquals(24, schedule.getEntries().size)
    }

    @Test
    fun `mapProfileToBasalSchedule should use correct rates from profile`() {
        val profile = mock(Profile::class.java)

        // Set up specific basal rates
        whenever(profile.getBasalTimeFromMidnight(0)).thenReturn(0.8)
        whenever(profile.getBasalTimeFromMidnight(6 * 60 * 60)).thenReturn(1.2)
        whenever(profile.getBasalTimeFromMidnight(12 * 60 * 60)).thenReturn(1.5)
        whenever(profile.getBasalTimeFromMidnight(18 * 60 * 60)).thenReturn(0.9)

        // Fill in the rest
        for (i in 0..23) {
            if (i != 0 && i != 6 && i != 12 && i != 18) {
                whenever(profile.getBasalTimeFromMidnight(i * 60 * 60)).thenReturn(1.0)
            }
        }

        val schedule = BasalSchedule.mapProfileToBasalSchedule(profile)

        assertEquals(0.8, schedule.rateAt(Duration.ZERO), 0.001)
        assertEquals(1.2, schedule.rateAt(Duration.standardHours(6)), 0.001)
        assertEquals(1.5, schedule.rateAt(Duration.standardHours(12)), 0.001)
        assertEquals(0.9, schedule.rateAt(Duration.standardHours(18)), 0.001)
    }

    @Test
    fun `mapProfileToBasalSchedule should start at midnight`() {
        val profile = mock(Profile::class.java)

        for (i in 0..23) {
            whenever(profile.getBasalTimeFromMidnight(i * 60 * 60)).thenReturn(1.0)
        }

        val schedule = BasalSchedule.mapProfileToBasalSchedule(profile)
        val entries = schedule.getEntries()

        assertEquals(Duration.ZERO, entries[0].startTime)
    }

    @Test
    fun `mapProfileToBasalSchedule should have entries at hourly intervals`() {
        val profile = mock(Profile::class.java)

        for (i in 0..23) {
            whenever(profile.getBasalTimeFromMidnight(i * 60 * 60)).thenReturn(1.0)
        }

        val schedule = BasalSchedule.mapProfileToBasalSchedule(profile)
        val entries = schedule.getEntries()

        for (i in 0..23) {
            assertEquals(Duration.standardHours(i.toLong()), entries[i].startTime)
        }
    }

    @Test
    fun `single entry schedule should work correctly`() {
        val entries = listOf(BasalScheduleEntry(1.5, Duration.ZERO))
        val schedule = BasalSchedule(entries)

        // All times should return the same rate
        assertEquals(1.5, schedule.rateAt(Duration.ZERO), 0.001)
        assertEquals(1.5, schedule.rateAt(Duration.standardHours(6)), 0.001)
        assertEquals(1.5, schedule.rateAt(Duration.standardHours(12)), 0.001)
        assertEquals(1.5, schedule.rateAt(Duration.standardHours(18)), 0.001)
    }

    @Test
    fun `schedule with many entries should work correctly`() {
        val entries = mutableListOf<BasalScheduleEntry>()
        for (i in 0..23) {
            entries.add(BasalScheduleEntry(1.0 + i * 0.1, Duration.standardHours(i.toLong())))
        }
        val schedule = BasalSchedule(entries)

        for (i in 0..23) {
            assertEquals(1.0 + i * 0.1, schedule.rateAt(Duration.standardHours(i.toLong())), 0.001)
        }
    }
}
