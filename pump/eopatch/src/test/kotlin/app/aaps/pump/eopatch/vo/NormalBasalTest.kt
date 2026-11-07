package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.AppConstant
import app.aaps.pump.eopatch.code.BasalStatus
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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
    fun `doseUnitPerSegmentArray should return correct array size`() {
        val normalBasal = NormalBasal()

        val array = normalBasal.doseUnitPerSegmentArray

        assertThat(array).hasLength(AppConstant.SEGMENT_COUNT_MAX)
    }

    // ---- #2454: what the patch really gives ----

    private fun basal(vararg blocks: BasalSegment) = NormalBasal().apply {
        list.clear()
        list.addAll(blocks)
    }

    private fun allDay(rate: Float) = basal(BasalSegment.create(0, 1440, rate))

    @Test
    fun `0_42 is given as 0_40 an hour, not 0_45 as before`() {
        val normalBasal = allDay(0.42f)

        assertThat(normalBasal.doseUnitPerSegmentArray.toList()).containsExactlyElementsIn(List(48) { 0.2f })
        assertThat(normalBasal.deliveredDoseUnitPerHour).containsExactlyElementsIn(List(24) { 0.4 })
    }

    @Test
    fun `0_45 alternates 0_25 and 0_20 and gives 0_45 every hour`() {
        val normalBasal = allDay(0.45f)

        assertThat(normalBasal.doseUnitPerSegmentArray.toList()).containsExactlyElementsIn(List(48) { if (it % 2 == 0) 0.25f else 0.2f }).inOrder()
        assertThat(normalBasal.deliveredDoseUnitPerHour).containsExactlyElementsIn(List(24) { 0.45 })
    }

    /** The patch gives 0.05 U steps per half hour: 0.05 U/h is one step each hour, never zero. */
    @Test
    fun `0_05 gives one step each hour`() {
        val normalBasal = allDay(0.05f)

        assertThat(normalBasal.doseUnitPerSegmentArray.toList()).containsExactlyElementsIn(List(48) { if (it % 2 == 0) 0.05f else 0f }).inOrder()
        assertThat(normalBasal.deliveredDoseUnitPerHour).containsExactlyElementsIn(List(24) { 0.05 })
    }

    /** Most pumps cannot give zero basal: rounding must never stop the insulin. */
    @Test
    fun `minimal rates round to the nearest step but never to zero`() {
        assertThat(NormalBasal.deliveredRate(0.01f)).isEqualTo(0.05f)
        assertThat(NormalBasal.deliveredRate(0.02f)).isEqualTo(0.05f)
        assertThat(NormalBasal.deliveredRate(0.05f)).isEqualTo(0.05f)
        assertThat(NormalBasal.deliveredRate(0.06f)).isEqualTo(0.05f)
        assertThat(NormalBasal.deliveredRate(0.074f)).isEqualTo(0.05f)
        assertThat(NormalBasal.deliveredRate(0.075f)).isEqualTo(0.1f)
        assertThat(NormalBasal.deliveredRate(0.124f)).isEqualTo(0.1f)
        assertThat(NormalBasal.deliveredRate(0.125f)).isEqualTo(0.15f)
        assertThat(NormalBasal.deliveredRate(0f)).isEqualTo(0f)
    }

    /** With the old split by the clock, a 0.05 block at half past got the smaller half: zero. */
    @Test
    fun `a half-hour block at half past still gets its step`() {
        val normalBasal = basal(BasalSegment.create(0, 390, 1.0f), BasalSegment.create(390, 420, 0.05f), BasalSegment.create(420, 1440, 1.0f))

        assertThat(normalBasal.doseUnitPerSegmentArray[13]).isEqualTo(0.05f)
    }

    /** The running profile holds hour values only: an hour split between two blocks holds both halves. */
    @Test
    fun `an hour split between two blocks holds the insulin of both halves`() {
        val normalBasal = basal(BasalSegment.create(0, 390, 1.0f), BasalSegment.create(390, 1440, 0.5f))

        assertThat(normalBasal.deliveredDoseUnitPerHour[5]).isEqualTo(1.0)
        assertThat(normalBasal.deliveredDoseUnitPerHour[6]).isEqualTo(0.75)
        assertThat(normalBasal.deliveredDoseUnitPerHour[7]).isEqualTo(0.5)
    }

    /** Every rate the patch takes (0.05 - 15 U/h) is given as the nearest 0.05 every hour, and never less than 0.05. */
    @Test
    fun `every rate in the pump range is given as the nearest step`() {
        for (hundredths in 5..1500) {
            val rate = hundredths / 100f
            val expected = maxOf(Math.round(hundredths / 5.0) * 5, 5) / 100.0
            val delivered = allDay(rate).deliveredDoseUnitPerHour
            assertWithMessage("rate $rate").that(delivered.toSet()).containsExactly(expected)
        }
    }

    @Test
    fun `the current hour reports what the patch gives, not the profile rate`() {
        assertThat(allDay(0.42f).currentDeliveredDoseUnitPerHour).isEqualTo(0.4)
    }

    /** After the update the patch still runs the program it got before: 0.42 as 0.25 + 0.20. */
    @Test
    fun `the doses the patch accepted are reported, not the ones it would get now`() {
        val normalBasal = allDay(0.42f).apply { sentDoses = FloatArray(48) { if (it % 2 == 0) 0.25f else 0.2f } }

        assertThat(normalBasal.deliveredDoseUnitPerHour).containsExactlyElementsIn(List(24) { 0.45 })
        assertThat(normalBasal.isSentWithCurrentRounding).isFalse()
    }

    @Test
    fun `a basal sent with the current rounding is known as such`() {
        val normalBasal = allDay(0.42f)
        assertThat(normalBasal.isSentWithCurrentRounding).isFalse()

        normalBasal.sentDoses = normalBasal.doseUnitPerSegmentArray

        assertThat(normalBasal.isSentWithCurrentRounding).isTrue()
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
    fun `toString should contain key information`() {
        val normalBasal = NormalBasal()

        val stringRep = normalBasal.toString()

        assertThat(stringRep).contains("NormalBasal")
        assertThat(stringRep).contains("status=")
        assertThat(stringRep).contains("list=")
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
}
