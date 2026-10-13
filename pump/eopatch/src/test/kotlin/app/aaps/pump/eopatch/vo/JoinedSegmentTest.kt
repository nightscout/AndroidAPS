package app.aaps.pump.eopatch.vo

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class JoinedSegmentTest {

    @Test
    fun `should initialize with correct start and end minutes for index 0`() {
        val segment = JoinedSegment(0)

        assertThat(segment.startMinute).isEqualTo(0)
        assertThat(segment.endMinute).isEqualTo(30)
    }

    @Test
    fun `should initialize with correct start and end minutes for index 1`() {
        val segment = JoinedSegment(1)

        assertThat(segment.startMinute).isEqualTo(30)
        assertThat(segment.endMinute).isEqualTo(60)
    }

    @Test
    fun `should initialize with correct start and end minutes for index 10`() {
        val segment = JoinedSegment(10)

        assertThat(segment.startMinute).isEqualTo(300)
        assertThat(segment.endMinute).isEqualTo(330)
    }

    @Test
    fun `should initialize with correct start and end minutes for last segment of day`() {
        // Last segment index is 47 (0-47 = 48 segments)
        val segment = JoinedSegment(47)

        assertThat(segment.startMinute).isEqualTo(1410) // 47 * 30
        assertThat(segment.endMinute).isEqualTo(1440) // 47 * 30 + 30 = 24 hours
    }

    @Test
    fun `should have 30 minute duration`() {
        val segment = JoinedSegment(5)

        val duration = segment.endMinute - segment.startMinute

        assertThat(duration).isEqualTo(30)
    }

    @Test
    fun `should use TIME_BASE constant for segment size`() {
        val segment = JoinedSegment(3)

        assertThat(segment.endMinute - segment.startMinute).isEqualTo(SegmentEntity.TIME_BASE)
    }

    @Test
    fun `should initialize no field to 0`() {
        val segment = JoinedSegment(5)

        assertThat(segment.no).isEqualTo(0)
    }

    @Test
    fun `should initialize doseUnitPerHour to 0`() {
        val segment = JoinedSegment(5)

        assertThat(segment.doseUnitPerHour).isEqualTo(0f)
    }

    @Test
    fun `should allow setting no field`() {
        val segment = JoinedSegment(0)
        segment.no = 42

        assertThat(segment.no).isEqualTo(42)
    }

    @Test
    fun `should allow setting doseUnitPerHour field`() {
        val segment = JoinedSegment(0)
        segment.doseUnitPerHour = 1.5f

        assertThat(segment.doseUnitPerHour).isEqualTo(1.5f)
    }

    @Test
    fun `should handle edge case of index 0`() {
        val segment = JoinedSegment(0)

        assertThat(segment.startMinute).isEqualTo(0)
        assertThat(segment.endMinute).isEqualTo(30)
        assertThat(segment.endMinute - segment.startMinute).isEqualTo(30)
    }

    @Test
    fun `multiple segments should have sequential time ranges`() {
        val segment1 = JoinedSegment(0)
        val segment2 = JoinedSegment(1)
        val segment3 = JoinedSegment(2)

        // Segment 2 should start where segment 1 ends
        assertThat(segment2.startMinute).isEqualTo(segment1.endMinute)
        assertThat(segment3.startMinute).isEqualTo(segment2.endMinute)
    }
}
