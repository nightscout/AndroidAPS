package app.aaps.pump.eopatch.vo

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BasalSegmentTest {

    @Test
    fun `create should create basal segment with correct values`() {
        val segment = BasalSegment.create(0, 60, 1.0f)

        assertThat(segment.start).isEqualTo(0)
        assertThat(segment.end).isEqualTo(60)
        assertThat(segment.doseUnitPerHour).isWithin(0.001f).of(1.0f)
        assertThat(segment.startMinute).isEqualTo(0)
        assertThat(segment.endMinute).isEqualTo(60)
    }

    @Test
    fun `create with only dose should create full day segment`() {
        val segment = BasalSegment.create(0.75f)

        assertThat(segment.start).isEqualTo(0)
        assertThat(segment.end).isEqualTo(1440) // 24 hours * 60 minutes
        assertThat(segment.doseUnitPerHour).isWithin(0.001f).of(0.75f)
    }

    @Test
    fun `isEmpty should return true when dose is zero`() {
        val segment = BasalSegment.create(0, 60, 0f)

        assertThat(segment.isEmpty).isTrue()
    }

    @Test
    fun `isEmpty should return false when dose is non-zero`() {
        val segment = BasalSegment.create(0, 60, 0.5f)

        assertThat(segment.isEmpty).isFalse()
    }

    @Test
    fun `constructor should reject invalid start time`() {
        assertThrows(IllegalArgumentException::class.java) {
            BasalSegment(-1, 60, 1.0f) // negative start
        }
    }

    @Test
    fun `constructor should reject invalid end time`() {
        assertThrows(IllegalArgumentException::class.java) {
            BasalSegment(60, 0, 1.0f) // end <= start
        }

        assertThrows(IllegalArgumentException::class.java) {
            BasalSegment(60, 60, 1.0f) // end == start
        }
    }

    @Test
    fun `constructor should reject non-30-minute-aligned times`() {
        assertThrows(IllegalArgumentException::class.java) {
            BasalSegment(0, 45, 1.0f) // 45 is not divisible by 30
        }

        assertThrows(IllegalArgumentException::class.java) {
            BasalSegment(15, 60, 1.0f) // 15 is not divisible by 30
        }
    }

    @Test
    fun `constructor should reject negative dose`() {
        assertThrows(IllegalArgumentException::class.java) {
            BasalSegment(0, 60, -0.5f)
        }
    }

    @Test
    fun `duplicate should create new segment with same dose`() {
        val original = BasalSegment.create(0, 60, 1.5f)
        val duplicate = original.duplicate(60, 120)

        assertThat(duplicate.start).isEqualTo(60)
        assertThat(duplicate.end).isEqualTo(120)
        assertThat(duplicate.doseUnitPerHour).isWithin(0.001f).of(1.5f)
    }

    @Test
    fun `deep should create exact copy`() {
        val original = BasalSegment.create(30, 90, 2.0f)
        val deep = original.deep()

        assertThat(deep).isNotSameInstanceAs(original)
        assertThat(deep.start).isEqualTo(original.start)
        assertThat(deep.end).isEqualTo(original.end)
        assertThat(deep.doseUnitPerHour).isEqualTo(original.doseUnitPerHour)
    }

    @Test
    fun `equalValue should return true for same dose`() {
        val segment1 = BasalSegment.create(0, 60, 1.0f)
        val segment2 = BasalSegment.create(60, 120, 1.0f)

        assertThat(segment1.equalValue(segment2)).isTrue()
    }

    @Test
    fun `equalValue should return false for different dose`() {
        val segment1 = BasalSegment.create(0, 60, 1.0f)
        val segment2 = BasalSegment.create(60, 120, 1.5f)

        assertThat(segment1.equalValue(segment2)).isFalse()
    }

    @Test
    fun `startIndex and endIndex should be calculated correctly`() {
        val segment = BasalSegment.create(60, 120, 1.0f) // 1:00 to 2:00

        // Each segment is 30 minutes, so:
        // 60 minutes = index 2
        // 120 minutes = index 4
        assertThat(segment.startIndex).isEqualTo(2)
        assertThat(segment.endIndex).isEqualTo(4)
    }

    @Test
    fun `segments should handle midnight boundary`() {
        val segment = BasalSegment.create(0, 30, 0.5f) // 00:00 to 00:30

        assertThat(segment.startIndex).isEqualTo(0)
        assertThat(segment.endIndex).isEqualTo(1)
    }

    @Test
    fun `segments should handle late night hours`() {
        val segment = BasalSegment.create(1410, 1440, 0.8f) // 23:30 to 24:00

        assertThat(segment.startIndex).isEqualTo(47)
        assertThat(segment.endIndex).isEqualTo(48)
    }
}
