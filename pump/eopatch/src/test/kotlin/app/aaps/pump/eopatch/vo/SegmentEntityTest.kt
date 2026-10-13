package app.aaps.pump.eopatch.vo

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for SegmentEntity abstract class methods using BasalSegment as concrete implementation
 */
class SegmentEntityTest {

    @Test
    fun `getDuration should return correct duration`() {
        val segment = BasalSegment.create(0, 60, 1.0f)

        assertThat(segment.getDuration()).isEqualTo(60)
    }

    @Test
    fun `getDuration should work for different time ranges`() {
        val segment1 = BasalSegment.create(30, 90, 1.0f)
        assertThat(segment1.getDuration()).isEqualTo(60)

        val segment2 = BasalSegment.create(0, 120, 1.0f)
        assertThat(segment2.getDuration()).isEqualTo(120)

        val segment3 = BasalSegment.create(0, 1440, 1.0f) // Full day
        assertThat(segment3.getDuration()).isEqualTo(1440)
    }

    @Test
    fun `isMinuteIncluding should return true for minutes within range`() {
        val segment = BasalSegment.create(60, 120, 1.0f) // 1:00 to 2:00

        assertThat(segment.isMinuteIncluding(60)).isTrue() // Start boundary
        assertThat(segment.isMinuteIncluding(90)).isTrue() // Middle
        assertThat(segment.isMinuteIncluding(119)).isTrue() // Just before end
    }

    @Test
    fun `isMinuteIncluding should return false for minutes outside range`() {
        val segment = BasalSegment.create(60, 120, 1.0f)

        assertThat(segment.isMinuteIncluding(59)).isFalse() // Before start
        assertThat(segment.isMinuteIncluding(120)).isFalse() // At end (exclusive)
        assertThat(segment.isMinuteIncluding(121)).isFalse() // After end
    }

    @Test
    fun `isSame should return true for identical segments`() {
        val segment1 = BasalSegment.create(60, 120, 1.0f)
        val segment2 = BasalSegment.create(60, 120, 2.0f) // Different dose, same time

        assertThat(segment1.isSame(segment2)).isTrue()
    }

    @Test
    fun `isSame should return false for different segments`() {
        val segment1 = BasalSegment.create(60, 120, 1.0f)
        val segment2 = BasalSegment.create(60, 180, 1.0f) // Different end
        val segment3 = BasalSegment.create(30, 120, 1.0f) // Different start

        assertThat(segment1.isSame(segment2)).isFalse()
        assertThat(segment1.isSame(segment3)).isFalse()
    }

    @Test
    fun `hasSame should return true when start or end matches`() {
        val segment1 = BasalSegment.create(60, 120, 1.0f)
        val segment2 = BasalSegment.create(60, 180, 1.0f) // Same start
        val segment3 = BasalSegment.create(30, 120, 1.0f) // Same end

        assertThat(segment1.hasSame(segment2)).isTrue()
        assertThat(segment1.hasSame(segment3)).isTrue()
    }

    @Test
    fun `hasSame should return false when neither start nor end matches`() {
        val segment1 = BasalSegment.create(60, 120, 1.0f)
        val segment2 = BasalSegment.create(90, 150, 1.0f)

        assertThat(segment1.hasSame(segment2)).isFalse()
    }

    @Test
    fun `canCover should return true when segment fully covers target`() {
        val segment = BasalSegment.create(60, 180, 1.0f)
        val target = BasalSegment.create(90, 150, 1.0f)

        assertThat(segment.canCover(target)).isTrue()
    }

    @Test
    fun `canCover should return true when segments are identical`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(60, 120, 2.0f)

        assertThat(segment.canCover(target)).isTrue()
    }

    @Test
    fun `canCover should return false when segment only partially covers target`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(90, 150, 1.0f)

        assertThat(segment.canCover(target)).isFalse()
    }

    @Test
    fun `canCover should return false when segment does not cover target at all`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(180, 240, 1.0f)

        assertThat(segment.canCover(target)).isFalse()
    }

    @Test
    fun `isCoveredBy should return true when covered by target`() {
        val segment = BasalSegment.create(90, 150, 1.0f)
        val target = BasalSegment.create(60, 180, 1.0f)

        assertThat(segment.isCoveredBy(target)).isTrue()
    }

    @Test
    fun `isCoveredBy should return false when not covered`() {
        val segment = BasalSegment.create(60, 180, 1.0f)
        val target = BasalSegment.create(90, 150, 1.0f)

        assertThat(segment.isCoveredBy(target)).isFalse()
    }

    @Test
    fun `isOverlapped should return true for overlapping segments`() {
        val segment1 = BasalSegment.create(60, 120, 1.0f)
        val segment2 = BasalSegment.create(90, 150, 1.0f)

        assertThat(segment1.isOverlapped(segment2)).isTrue()
        assertThat(segment2.isOverlapped(segment1)).isTrue()
    }

    @Test
    fun `isOverlapped should return false for non-overlapping segments`() {
        val segment1 = BasalSegment.create(60, 120, 1.0f)
        val segment2 = BasalSegment.create(120, 180, 1.0f)

        assertThat(segment1.isOverlapped(segment2)).isFalse()
        assertThat(segment2.isOverlapped(segment1)).isFalse()
    }

    @Test
    fun `isOverlapped should return true when one segment covers another`() {
        val segment1 = BasalSegment.create(60, 180, 1.0f)
        val segment2 = BasalSegment.create(90, 120, 1.0f)

        assertThat(segment1.isOverlapped(segment2)).isTrue()
        assertThat(segment2.isOverlapped(segment1)).isTrue()
    }

    @Test
    fun `isPartiallyNotFullyIncluding should return true for partial overlap`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(90, 150, 1.0f)

        assertThat(segment.isPartiallyNotFullyIncluding(target)).isTrue()
        assertThat(target.isPartiallyNotFullyIncluding(segment)).isTrue()
    }

    @Test
    fun `isPartiallyNotFullyIncluding should return false when one fully covers another`() {
        val segment = BasalSegment.create(60, 180, 1.0f)
        val target = BasalSegment.create(90, 120, 1.0f)

        assertThat(segment.isPartiallyNotFullyIncluding(target)).isFalse()
        assertThat(target.isPartiallyNotFullyIncluding(segment)).isFalse()
    }

    @Test
    fun `isPartiallyNotFullyIncluding should return false for non-overlapping segments`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(180, 240, 1.0f)

        assertThat(segment.isPartiallyNotFullyIncluding(target)).isFalse()
    }

    @Test
    fun `subtract should adjust start when target overlaps from left`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(30, 90, 1.0f)

        segment.subtract(target, false)

        assertThat(segment.startMinute).isEqualTo(90)
        assertThat(segment.endMinute).isEqualTo(120)
    }

    @Test
    fun `subtract should adjust end when target overlaps from right`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(90, 150, 1.0f)

        segment.subtract(target, false)

        assertThat(segment.startMinute).isEqualTo(60)
        assertThat(segment.endMinute).isEqualTo(90)
    }

    @Test
    fun `subtract with validCheck should not modify when not partially overlapping`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(180, 240, 1.0f)

        segment.subtract(target, true)

        assertThat(segment.startMinute).isEqualTo(60)
        assertThat(segment.endMinute).isEqualTo(120)
    }

    @Test
    fun `splitBy should return two segments when covering target`() {
        val segment = BasalSegment.create(60, 180, 1.0f)
        val target = BasalSegment.create(90, 120, 1.0f)

        val result = segment.splitBy(target, false)

        assertThat(result).isNotNull()
        assertThat(result).hasSize(2)
        assertThat(result!![0].startMinute).isEqualTo(60)
        assertThat(result[0].endMinute).isEqualTo(90)
        assertThat(result[1].startMinute).isEqualTo(120)
        assertThat(result[1].endMinute).isEqualTo(180)
    }

    @Test
    fun `splitBy should return one segment when target is at start`() {
        val segment = BasalSegment.create(60, 180, 1.0f)
        val target = BasalSegment.create(60, 90, 1.0f)

        val result = segment.splitBy(target, false)

        assertThat(result).isNotNull()
        assertThat(result).hasSize(1)
        assertThat(result!![0].startMinute).isEqualTo(90)
        assertThat(result[0].endMinute).isEqualTo(180)
    }

    @Test
    fun `splitBy should return one segment when target is at end`() {
        val segment = BasalSegment.create(60, 180, 1.0f)
        val target = BasalSegment.create(150, 180, 1.0f)

        val result = segment.splitBy(target, false)

        assertThat(result).isNotNull()
        assertThat(result).hasSize(1)
        assertThat(result!![0].startMinute).isEqualTo(60)
        assertThat(result[0].endMinute).isEqualTo(150)
    }

    @Test
    fun `splitBy should return null when not covering with validCheck`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val target = BasalSegment.create(90, 150, 1.0f)

        val result = segment.splitBy(target, true)

        assertThat(result).isNull()
    }

    @Test
    fun `includes should return true for JoinedSegment within range`() {
        val segment = BasalSegment.create(60, 180, 1.0f)
        val joined = JoinedSegment(2) // Index 2 = 60-90 minutes

        assertThat(segment.includes(joined)).isTrue()
    }

    @Test
    fun `includes should return false for JoinedSegment outside range`() {
        val segment = BasalSegment.create(60, 120, 1.0f)
        val joined = JoinedSegment(10) // Index 10 = 300-330 minutes

        assertThat(segment.includes(joined)).isFalse()
    }

    @Test
    fun `TIME_BASE should be 30 minutes`() {
        assertThat(SegmentEntity.TIME_BASE).isEqualTo(30)
    }

    @Test
    fun `startIndex should be based on TIME_BASE`() {
        val segment = BasalSegment.create(90, 150, 1.0f)

        assertThat(segment.startIndex).isEqualTo(3) // 90 / 30 = 3
        assertThat(segment.endIndex).isEqualTo(5) // 150 / 30 = 5
    }
}
