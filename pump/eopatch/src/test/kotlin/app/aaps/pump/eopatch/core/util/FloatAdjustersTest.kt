package app.aaps.pump.eopatch.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FloatAdjustersTest {

    // CEIL2_BASAL_RATE: rounds UP to nearest 0.05 step
    @Test
    fun `CEIL2_BASAL_RATE should ceil to 0_05 steps`() {
        assertThat(FloatAdjusters.CEIL2_BASAL_RATE(0.01f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.CEIL2_BASAL_RATE(0.04f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.CEIL2_BASAL_RATE(0.05f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.CEIL2_BASAL_RATE(0.06f)).isEqualTo(0.10f)
        assertThat(FloatAdjusters.CEIL2_BASAL_RATE(0.10f)).isEqualTo(0.10f)
    }

    // FLOOR2_INSULIN / FLOOR2_BOLUS: rounds DOWN to nearest 0.05 step
    @Test
    fun `FLOOR2_INSULIN should floor to 0_05 steps`() {
        assertThat(FloatAdjusters.FLOOR2_INSULIN(0.01f)).isEqualTo(0.00f)
        assertThat(FloatAdjusters.FLOOR2_INSULIN(0.04f)).isEqualTo(0.00f)
        assertThat(FloatAdjusters.FLOOR2_INSULIN(0.05f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.FLOOR2_INSULIN(0.06f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.FLOOR2_INSULIN(0.09f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.FLOOR2_INSULIN(0.10f)).isEqualTo(0.10f)
    }

    @Test
    fun `FLOOR2_BOLUS should behave same as FLOOR2_INSULIN`() {
        assertThat(FloatAdjusters.FLOOR2_BOLUS(0.07f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.FLOOR2_BOLUS(1.23f)).isEqualTo(1.20f)
    }

    @Test
    fun `FLOOR2_BASAL_RATE should floor to 0_05 steps`() {
        assertThat(FloatAdjusters.FLOOR2_BASAL_RATE(0.07f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.FLOOR2_BASAL_RATE(1.0f)).isEqualTo(1.0f)
    }

    // ROUND2_INSULIN / ROUND2_BASAL_RATE: rounds to nearest 0.05 step
    @Test
    fun `ROUND2_INSULIN should round to nearest 0_05 step`() {
        assertThat(FloatAdjusters.ROUND2_INSULIN(0.01f)).isEqualTo(0.00f)
        assertThat(FloatAdjusters.ROUND2_INSULIN(0.02f)).isEqualTo(0.00f)
        assertThat(FloatAdjusters.ROUND2_INSULIN(0.03f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.ROUND2_INSULIN(0.05f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.ROUND2_INSULIN(0.07f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.ROUND2_INSULIN(0.08f)).isEqualTo(0.10f)
        assertThat(FloatAdjusters.ROUND2_INSULIN(0.10f)).isEqualTo(0.10f)
    }

    @Test
    fun `ROUND2_BASAL_RATE should round to nearest 0_05 step`() {
        assertThat(FloatAdjusters.ROUND2_BASAL_RATE(0.03f)).isEqualTo(0.05f)
        assertThat(FloatAdjusters.ROUND2_BASAL_RATE(1.0f)).isEqualTo(1.0f)
    }

    // Edge cases
    @Test
    fun `adjusters should handle zero`() {
        assertThat(FloatAdjusters.FLOOR2_INSULIN(0.0f)).isEqualTo(0.0f)
        assertThat(FloatAdjusters.CEIL2_BASAL_RATE(0.0f)).isEqualTo(0.0f)
        assertThat(FloatAdjusters.ROUND2_INSULIN(0.0f)).isEqualTo(0.0f)
    }

    @Test
    fun `adjusters should handle larger values`() {
        assertThat(FloatAdjusters.FLOOR2_INSULIN(15.0f)).isEqualTo(15.0f)
        assertThat(FloatAdjusters.FLOOR2_INSULIN(15.03f)).isEqualTo(15.0f)
        assertThat(FloatAdjusters.ROUND2_INSULIN(15.03f)).isEqualTo(15.05f)
    }
}
