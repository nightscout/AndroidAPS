package app.aaps.pump.eopatch.vo

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for SegmentsEntity abstract class methods using NormalBasal as concrete implementation
 */
class SegmentsEntityTest {

    @Test
    fun `hasSegments should return false when empty`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization

        assertThat(normalBasal.hasSegments()).isFalse()
    }

    @Test
    fun `hasSegments should return true when segments exist`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 60, 1.0f))

        assertThat(normalBasal.hasSegments()).isTrue()
    }

    @Test
    fun `segmentCount should return correct count`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization

        assertThat(normalBasal.segmentCount).isEqualTo(0)

        normalBasal.list.add(BasalSegment.create(0, 60, 1.0f))
        assertThat(normalBasal.segmentCount).isEqualTo(1)

        normalBasal.list.add(BasalSegment.create(60, 120, 1.5f))
        assertThat(normalBasal.segmentCount).isEqualTo(2)
    }

    @Test
    fun `copiedSegmentList should return copy of list`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        val segment = BasalSegment.create(0, 60, 1.0f)
        normalBasal.list.add(segment)

        val copy = normalBasal.copiedSegmentList

        assertThat(copy).isNotSameInstanceAs(normalBasal.list)
        assertThat(copy).hasSize(1)
        assertThat(copy[0]).isSameInstanceAs(segment)
    }

    @Test
    fun `deepCopiedSegmentList should return deep copy`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        val segment = BasalSegment.create(0, 60, 1.0f)
        normalBasal.list.add(segment)

        val deepCopy = normalBasal.deepCopiedSegmentList

        assertThat(deepCopy).isNotSameInstanceAs(normalBasal.list)
        assertThat(deepCopy).hasSize(1)
        assertThat(deepCopy[0]).isNotSameInstanceAs(segment)
        assertThat(deepCopy[0].doseUnitPerHour).isEqualTo(segment.doseUnitPerHour)
    }

    @Test
    fun `isValid should return false for empty list when allowEmpty is false`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization

        assertThat(normalBasal.isValid(false)).isFalse()
    }

    @Test
    fun `isValid should return true for empty list when allowEmpty is true`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization

        assertThat(normalBasal.isValid(true)).isTrue()
    }

    @Test
    fun `isValid should return false when list contains empty segment`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 60, 1.0f))
        normalBasal.list.add(BasalSegment.create(60, 120, 0f)) // Empty segment

        assertThat(normalBasal.isValid(true)).isFalse()
    }

    @Test
    fun `isValid should return true when all segments are non-empty`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 60, 1.0f))
        normalBasal.list.add(BasalSegment.create(60, 120, 1.5f))

        assertThat(normalBasal.isValid(true)).isTrue()
    }

    @Test
    fun `isFullSegment should return true for complete 24 hour coverage`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 720, 1.0f))
        normalBasal.list.add(BasalSegment.create(720, 1440, 1.5f))

        assertThat(normalBasal.isFullSegment()).isTrue()
    }

    @Test
    fun `isFullSegment should return false for incomplete coverage`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 720, 1.0f))
        normalBasal.list.add(BasalSegment.create(720, 1200, 1.5f)) // Ends at 1200, not 1440

        assertThat(normalBasal.isFullSegment()).isFalse()
    }

    @Test
    fun `isFullSegment should return false for gaps in coverage`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 600, 1.0f))
        normalBasal.list.add(BasalSegment.create(720, 1440, 1.5f)) // Gap from 600-720

        assertThat(normalBasal.isFullSegment()).isFalse()
    }

    @Test
    fun `isFullSegment should return false for empty list`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization

        assertThat(normalBasal.isFullSegment()).isFalse()
    }

    @Test
    fun `getEmptySegment should return full day for empty list`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization

        val empty = normalBasal.getEmptySegment()

        assertThat(empty.first).isEqualTo(0)
        assertThat(empty.second).isEqualTo(48) // SEGMENT_COUNT_MAX
    }

    @Test
    fun `getEmptySegment should return gap at start if first segment doesn't start at 0`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(60, 1440, 1.0f)) // Starts at 60 instead of 0

        val empty = normalBasal.getEmptySegment()

        assertThat(empty.first).isEqualTo(0)
        assertThat(empty.second).isEqualTo(2) // Index 2 = 60 minutes
    }

    @Test
    fun `getEmptySegment should return gap at end if only one segment`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 720, 1.0f))

        val empty = normalBasal.getEmptySegment()

        assertThat(empty.first).isEqualTo(24) // Index 24 = 720 minutes
        assertThat(empty.second).isEqualTo(48) // SEGMENT_COUNT_MAX
    }

    @Test
    fun `getEmptySegment should return gap between segments`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 300, 1.0f))
        normalBasal.list.add(BasalSegment.create(600, 1440, 1.5f)) // Gap from 300-600

        val empty = normalBasal.getEmptySegment()

        assertThat(empty.first).isEqualTo(10) // Index 10 = 300 minutes
        assertThat(empty.second).isEqualTo(20) // Index 20 = 600 minutes
    }

    @Test
    fun `getEmptySegment should return last gap when full coverage`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 1440, 1.0f))

        val empty = normalBasal.getEmptySegment()

        assertThat(empty.first).isEqualTo(48)
        assertThat(empty.second).isEqualTo(48)
    }

    @Test
    fun `eachSegmentItem should iterate over all segment items`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 60, 1.0f)) // 2 items (30min each)
        normalBasal.list.add(BasalSegment.create(60, 120, 1.5f)) // 2 items

        val indices = mutableListOf<Int>()
        normalBasal.eachSegmentItem { index, _ ->
            indices.add(index)
            true
        }

        assertThat(indices).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `eachSegmentItem should stop when function returns false`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 120, 1.0f)) // 4 items

        val indices = mutableListOf<Int>()
        normalBasal.eachSegmentItem { index, _ ->
            indices.add(index)
            index < 2 // Stop after index 2
        }

        assertThat(indices).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `eachSegmentItem should provide correct segment for each index`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        val segment1 = BasalSegment.create(0, 60, 1.0f)
        val segment2 = BasalSegment.create(60, 120, 2.0f)
        normalBasal.list.add(segment1)
        normalBasal.list.add(segment2)

        val segmentMap = mutableMapOf<Int, BasalSegment>()
        normalBasal.eachSegmentItem { index, segment ->
            segmentMap[index] = segment
            true
        }

        assertThat(segmentMap[0]).isSameInstanceAs(segment1)
        assertThat(segmentMap[1]).isSameInstanceAs(segment1)
        assertThat(segmentMap[2]).isSameInstanceAs(segment2)
        assertThat(segmentMap[3]).isSameInstanceAs(segment2)
    }

    @Test
    fun `multiple segments should form continuous day coverage`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear() // Clear default initialization
        normalBasal.list.add(BasalSegment.create(0, 360, 1.0f))
        normalBasal.list.add(BasalSegment.create(360, 720, 1.5f))
        normalBasal.list.add(BasalSegment.create(720, 1080, 2.0f))
        normalBasal.list.add(BasalSegment.create(1080, 1440, 1.0f))

        assertThat(normalBasal.segmentCount).isEqualTo(4)
        assertThat(normalBasal.isFullSegment()).isTrue()
    }
}
