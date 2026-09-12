package app.aaps.plugins.aps.openAPS

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test

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

        assertEquals(50.0, tddStatus.tdd1D)
        assertEquals(48.0, tddStatus.tdd7D)
        assertEquals(52.0, tddStatus.tddLast24H)
        assertEquals(10.0, tddStatus.tddLast4H)
        assertEquals(8.0, tddStatus.tddLast8to4H)
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

        assertEquals(55.0, copy.tdd1D)
        assertEquals(48.0, copy.tdd7D)
        assertEquals(50.0, original.tdd1D) // Original unchanged
    }

    @Test
    fun `TddStatus equality works correctly`() {
        val status1 = TddStatus(50.0, 48.0, 52.0, 10.0, 8.0)
        val status2 = TddStatus(50.0, 48.0, 52.0, 10.0, 8.0)
        val status3 = TddStatus(51.0, 48.0, 52.0, 10.0, 8.0)

        assertEquals(status2, status1)
        assertNotEquals(status3, status1)
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

        assertEquals(0.0, tddStatus.tdd1D)
        assertEquals(0.0, tddStatus.tdd7D)
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

        assertEquals(-1.0, tddStatus.tdd1D)
        assertEquals(-4.0, tddStatus.tddLast4H)
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

        assertEquals(Double.MAX_VALUE, tddStatus.tdd1D)
        assertEquals(1000000.0, tddStatus.tdd7D)
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

        assertEquals(50.123, tddStatus.tdd1D, 0.001)
        assertEquals(8.999, tddStatus.tddLast8to4H, 0.001)
    }
}
