package app.aaps.pump.eopatch

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AppConstantTest {

    @Test
    fun `BASAL_MIN_AMOUNT should be 0_05f`() {
        assertThat(AppConstant.BASAL_MIN_AMOUNT).isEqualTo(0.05f)
    }

    @Test
    fun `INSULIN_UNIT_P should be 0_05f`() {
        assertThat(AppConstant.INSULIN_UNIT_P).isEqualTo(0.05f)
    }

    @Test
    fun `INSULIN_UNIT_STEP_U should equal INSULIN_UNIT_P`() {
        assertThat(AppConstant.INSULIN_UNIT_STEP_U).isEqualTo(AppConstant.INSULIN_UNIT_P)
        assertThat(AppConstant.INSULIN_UNIT_STEP_U).isEqualTo(0.05f)
    }

    @Test
    fun `OFF should be 0`() {
        assertThat(AppConstant.OFF).isEqualTo(0)
    }

    @Test
    fun `ON should be 1`() {
        assertThat(AppConstant.ON).isEqualTo(1)
    }

    @Test
    fun `PUMP_DURATION_MILLI should be 4 seconds`() {
        assertThat(AppConstant.PUMP_DURATION_MILLI).isEqualTo(4000L)
    }

    @Test
    fun `BASAL_RATE_PER_HOUR_MIN should equal BASAL_MIN_AMOUNT`() {
        assertThat(AppConstant.BASAL_RATE_PER_HOUR_MIN).isEqualTo(AppConstant.BASAL_MIN_AMOUNT)
        assertThat(AppConstant.BASAL_RATE_PER_HOUR_MIN).isEqualTo(0.05f)
    }

    @Test
    fun `SEGMENT_MAX_SIZE_48 should be 48`() {
        assertThat(AppConstant.SEGMENT_MAX_SIZE_48).isEqualTo(48)
    }

    @Test
    fun `SEGMENT_COUNT_MAX should equal SEGMENT_MAX_SIZE_48`() {
        assertThat(AppConstant.SEGMENT_COUNT_MAX).isEqualTo(AppConstant.SEGMENT_MAX_SIZE_48)
        assertThat(AppConstant.SEGMENT_COUNT_MAX).isEqualTo(48)
    }

    @Test
    fun `BOLUS_ACTIVE_EXTENDED_WAIT should be 0x2`() {
        assertThat(AppConstant.BOLUS_ACTIVE_EXTENDED_WAIT).isEqualTo(0x2)
        assertThat(AppConstant.BOLUS_ACTIVE_EXTENDED_WAIT).isEqualTo(2)
    }

    @Test
    fun `BOLUS_UNIT_STEP should equal INSULIN_UNIT_STEP_U`() {
        assertThat(AppConstant.BOLUS_UNIT_STEP).isEqualTo(AppConstant.INSULIN_UNIT_STEP_U)
        assertThat(AppConstant.BOLUS_UNIT_STEP).isEqualTo(0.05f)
    }

    @Test
    fun `DAY_START_MINUTE should be 0`() {
        assertThat(AppConstant.DAY_START_MINUTE).isEqualTo(0)
    }

    @Test
    fun `DAY_END_MINUTE should be 1440 (24 hours)`() {
        assertThat(AppConstant.DAY_END_MINUTE).isEqualTo(1440)
        assertThat(AppConstant.DAY_END_MINUTE).isEqualTo(24 * 60)
    }

    @Test
    fun `INSULIN_DURATION_MIN should be 2_0f`() {
        assertThat(AppConstant.INSULIN_DURATION_MIN).isEqualTo(2.0f)
    }

    @Test
    fun `basal constants should be consistent`() {
        assertThat(AppConstant.BASAL_MIN_AMOUNT).isEqualTo(AppConstant.BASAL_RATE_PER_HOUR_MIN)
    }

    @Test
    fun `insulin unit constants should be consistent`() {
        assertThat(AppConstant.INSULIN_UNIT_P).isEqualTo(AppConstant.INSULIN_UNIT_STEP_U)
        assertThat(AppConstant.INSULIN_UNIT_STEP_U).isEqualTo(AppConstant.BOLUS_UNIT_STEP)
    }

    @Test
    fun `segment constants should be consistent`() {
        assertThat(AppConstant.SEGMENT_MAX_SIZE_48).isEqualTo(AppConstant.SEGMENT_COUNT_MAX)
    }

    @Test
    fun `day time range should be valid`() {
        assertThat(AppConstant.DAY_START_MINUTE).isLessThan(AppConstant.DAY_END_MINUTE)
        assertThat(AppConstant.DAY_START_MINUTE).isEqualTo(0)
        assertThat(AppConstant.DAY_END_MINUTE - AppConstant.DAY_START_MINUTE).isEqualTo(1440) // 24 hours
    }
}
