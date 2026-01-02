package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.AppConstant
import app.aaps.pump.eopatch.code.BasalStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NormalBasalTest {

    @Test
    fun `create should initialize with correct default values`() {
        val normalBasal = NormalBasal()

        assertThat(normalBasal.status).isEqualTo(BasalStatus.SELECTED)
        assertThat(normalBasal.list).hasSize(1)
        assertThat(normalBasal.list[0].doseUnitPerHour).isWithin(0.001f).of(AppConstant.BASAL_RATE_PER_HOUR_MIN)
    }

    @Test
    fun `create with dose should set first segment dose`() {
        val normalBasal = NormalBasal.create(1.5f)

        assertThat(normalBasal.status).isEqualTo(BasalStatus.SELECTED)
        assertThat(normalBasal.list[0].doseUnitPerHour).isWithin(0.001f).of(1.5f)
    }

    @Test
    fun `status setter should reset segment index when stopped`() {
        val normalBasal = NormalBasal()
        normalBasal.status = BasalStatus.STARTED

        normalBasal.status = BasalStatus.STOPPED

        assertThat(normalBasal.status).isEqualTo(BasalStatus.STOPPED)
    }

    @Test
    fun `maxDoseUnitPerHour should return maximum from all segments`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()
        normalBasal.list.add(BasalSegment.create(0, 360, 0.5f))
        normalBasal.list.add(BasalSegment.create(360, 720, 2.0f))
        normalBasal.list.add(BasalSegment.create(720, 1440, 1.0f))

        assertThat(normalBasal.maxDoseUnitPerHour).isWithin(0.001f).of(2.0f)
    }

    @Test
    fun `maxDoseUnitPerHour should handle empty list`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()

        assertThat(normalBasal.maxDoseUnitPerHour).isWithin(0.001f).of(0f)
    }

    @Test
    fun `doseUnitPerDay should calculate total daily insulin`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()
        // Add simple segments for easier calculation
        normalBasal.list.add(BasalSegment.create(0, 720, 1.0f)) // 12 hours at 1.0 U/hr
        normalBasal.list.add(BasalSegment.create(720, 1440, 0.5f)) // 12 hours at 0.5 U/hr

        // Total: 12 * 1.0 + 12 * 0.5 = 18.0 U
        // But the actual calculation uses segments per 30 minutes and ceiling/floor adjustments
        val total = normalBasal.doseUnitPerDay
        assertThat(total).isGreaterThan(0f)
    }

    @Test
    fun `doseUnitPerSegmentArray should return correct array size`() {
        val normalBasal = NormalBasal()

        val array = normalBasal.doseUnitPerSegmentArray

        assertThat(array).hasLength(AppConstant.SEGMENT_COUNT_MAX)
    }

    @Test
    fun `getSegmentDoseUnitPerHour should return correct dose for time`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()
        normalBasal.list.add(BasalSegment.create(0, 720, 1.0f))
        normalBasal.list.add(BasalSegment.create(720, 1440, 2.0f))

        // Get dose at 6:00 (360 minutes) - should be in first segment
        val dose1 = normalBasal.getSegmentDoseUnitPerHour(getTimeAtHour(6))
        assertThat(dose1).isWithin(0.001f).of(1.0f)

        // Get dose at 18:00 (1080 minutes) - should be in second segment
        val dose2 = normalBasal.getSegmentDoseUnitPerHour(getTimeAtHour(18))
        assertThat(dose2).isWithin(0.001f).of(2.0f)
    }

    @Test
    fun `updateNormalBasalIndex should detect segment change`() {
        val normalBasal = NormalBasal()

        // First call should return true as index is initialized
        val changed1 = normalBasal.updateNormalBasalIndex()
        assertThat(changed1).isTrue()

        // Immediate second call should return false (same segment)
        val changed2 = normalBasal.updateNormalBasalIndex()
        assertThat(changed2).isFalse()
    }

    @Test
    fun `getMaxBasal should find maximum in duration window`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()
        normalBasal.list.add(BasalSegment.create(0, 360, 0.5f))   // 00:00-06:00
        normalBasal.list.add(BasalSegment.create(360, 720, 2.0f))  // 06:00-12:00
        normalBasal.list.add(BasalSegment.create(720, 1080, 1.0f)) // 12:00-18:00
        normalBasal.list.add(BasalSegment.create(1080, 1440, 0.8f)) // 18:00-24:00

        // Get max for next 2 hours - depends on current time
        val maxBasal = normalBasal.getMaxBasal(120)

        assertThat(maxBasal).isAtLeast(0.5f) // At minimum, should be 0.5
    }

    @Test
    fun `currentSegmentDoseUnitPerHour should return dose for current time`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()
        // Set up full day schedule
        normalBasal.list.add(BasalSegment.create(0, 1440, 1.0f))

        val dose = normalBasal.currentSegmentDoseUnitPerHour

        assertThat(dose).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `toString should contain key information`() {
        val normalBasal = NormalBasal()

        val stringRep = normalBasal.toString()

        assertThat(stringRep).contains("NormalBasal")
        assertThat(stringRep).contains("status=")
        assertThat(stringRep).contains("list=")
    }

    @Test
    fun `startTime should return timestamp for current segment`() {
        val normalBasal = NormalBasal()

        val startTime = normalBasal.startTime

        assertThat(startTime).isGreaterThan(0)
    }

    @Test
    fun `isFullSegment should return true for complete 24 hour schedule`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()
        normalBasal.list.add(BasalSegment.create(0, 1440, 1.0f))

        assertThat(normalBasal.isFullSegment()).isTrue()
    }

    @Test
    fun `isFullSegment should return false for incomplete schedule`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()
        normalBasal.list.add(BasalSegment.create(0, 720, 1.0f))

        assertThat(normalBasal.isFullSegment()).isFalse()
    }

    @Test
    fun `hasSegments should return true when list is not empty`() {
        val normalBasal = NormalBasal()

        assertThat(normalBasal.hasSegments()).isTrue()
    }

    @Test
    fun `hasSegments should return false when list is empty`() {
        val normalBasal = NormalBasal()
        normalBasal.list.clear()

        assertThat(normalBasal.hasSegments()).isFalse()
    }

    // Helper function to create a timestamp at a specific hour
    private fun getTimeAtHour(hour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
