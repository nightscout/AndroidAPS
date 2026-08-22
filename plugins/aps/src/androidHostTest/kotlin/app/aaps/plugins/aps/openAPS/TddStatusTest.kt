package app.aaps.plugins.aps.openAPS

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TddStatusTest {

    @Test
    fun `constructor creates TddStatus with all values`() {
        val tddStatus = TddStatus(
            tdd1D = 50.0,
            tdd7D = 48.0,
            tddLast24H = 52.0,
            tddLast4H = 10.0,
            tddLast8to4H = 8.0
        )

        assertThat(tddStatus.tdd1D).isEqualTo(50.0)
        assertThat(tddStatus.tdd7D).isEqualTo(48.0)
        assertThat(tddStatus.tddLast24H).isEqualTo(52.0)
        assertThat(tddStatus.tddLast4H).isEqualTo(10.0)
        assertThat(tddStatus.tddLast8to4H).isEqualTo(8.0)
    }

    @Test
    fun `TddStatus supports copy`() {
        val original = TddStatus(
            tdd1D = 50.0,
            tdd7D = 48.0,
            tddLast24H = 52.0,
            tddLast4H = 10.0,
            tddLast8to4H = 8.0
        )

        val copy = original.copy(tdd1D = 55.0)

        assertThat(copy.tdd1D).isEqualTo(55.0)
        assertThat(copy.tdd7D).isEqualTo(48.0)
        assertThat(original.tdd1D).isEqualTo(50.0) // Original unchanged
    }

    @Test
    fun `TddStatus equality works correctly`() {
        val status1 = TddStatus(50.0, 48.0, 52.0, 10.0, 8.0)
        val status2 = TddStatus(50.0, 48.0, 52.0, 10.0, 8.0)
        val status3 = TddStatus(51.0, 48.0, 52.0, 10.0, 8.0)

        assertThat(status1).isEqualTo(status2)
        assertThat(status1).isNotEqualTo(status3)
    }

    @Test
    fun `TddStatus with zero values`() {
        val tddStatus = TddStatus(
            tdd1D = 0.0,
            tdd7D = 0.0,
            tddLast24H = 0.0,
            tddLast4H = 0.0,
            tddLast8to4H = 0.0
        )

        assertThat(tddStatus.tdd1D).isEqualTo(0.0)
        assertThat(tddStatus.tdd7D).isEqualTo(0.0)
    }

    @Test
    fun `TddStatus with negative values`() {
        // Although unlikely in practice, data class should handle any double values
        val tddStatus = TddStatus(
            tdd1D = -1.0,
            tdd7D = -2.0,
            tddLast24H = -3.0,
            tddLast4H = -4.0,
            tddLast8to4H = -5.0
        )

        assertThat(tddStatus.tdd1D).isEqualTo(-1.0)
        assertThat(tddStatus.tddLast4H).isEqualTo(-4.0)
    }

    @Test
    fun `TddStatus with very large values`() {
        val tddStatus = TddStatus(
            tdd1D = Double.MAX_VALUE,
            tdd7D = 1000000.0,
            tddLast24H = 999999.9,
            tddLast4H = 500000.0,
            tddLast8to4H = 250000.0
        )

        assertThat(tddStatus.tdd1D).isEqualTo(Double.MAX_VALUE)
        assertThat(tddStatus.tdd7D).isEqualTo(1000000.0)
    }

    @Test
    fun `TddStatus with fractional values`() {
        val tddStatus = TddStatus(
            tdd1D = 50.123,
            tdd7D = 48.456,
            tddLast24H = 52.789,
            tddLast4H = 10.111,
            tddLast8to4H = 8.999
        )

        assertThat(tddStatus.tdd1D).isWithin(0.001).of(50.123)
        assertThat(tddStatus.tddLast8to4H).isWithin(0.001).of(8.999)
    }
}
