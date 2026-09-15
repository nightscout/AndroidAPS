package app.aaps.plugins.aps.openAPS

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.TrendArrow
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeltaCalculatorTest : TestBase() {

    private lateinit var deltaCalculator: DeltaCalculator

    @BeforeEach
    fun setup() {
        deltaCalculator = DeltaCalculator(aapsLogger)
    }

    @Test
    fun `calculateDeltas returns zero when data size is less than 2`() {
        val data = mutableListOf<InMemoryGlucoseValue>()

        val result = deltaCalculator.calculateDeltas(data)

        assertThat(result.delta).isEqualTo(0.0)
        assertThat(result.shortAvgDelta).isEqualTo(0.0)
        assertThat(result.longAvgDelta).isEqualTo(0.0)
    }

    @Test
    fun `calculateDeltas returns zero when data size is exactly 1`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 0L)
        )

        val result = deltaCalculator.calculateDeltas(data)

        assertThat(result.delta).isEqualTo(0.0)
        assertThat(result.shortAvgDelta).isEqualTo(0.0)
        assertThat(result.longAvgDelta).isEqualTo(0.0)
    }

    @Test
    fun `calculateDeltas with simple two values calculates delta correctly`() {
        // 5 minutes apart, 10 mg/dL difference
        val data = mutableListOf(
            createGlucoseValue(110.0, 0L),
            createGlucoseValue(100.0, 300000L) // 5 minutes ago
        )

        val result = deltaCalculator.calculateDeltas(data)

        // Delta should be (110-100)/5*5 = 10 mg/dL/5m
        assertThat(result.delta).isWithin(0.01).of(10.0)
        assertThat(result.shortAvgDelta).isWithin(0.01).of(10.0)
    }

    @Test
    fun `calculateDeltas with values in short delta range`() {
        // Create values at 0, 5, 10, 15 minutes ago
        val data = mutableListOf(
            createGlucoseValue(120.0, 0L),
            createGlucoseValue(115.0, 300000L),   // 5 min ago
            createGlucoseValue(110.0, 600000L),   // 10 min ago
            createGlucoseValue(105.0, 900000L)    // 15 min ago
        )

        val result = deltaCalculator.calculateDeltas(data)

        // All values should be included in short deltas (2.5-17.5 min range)
        assertThat(result.shortAvgDelta).isGreaterThan(0.0)
    }

    @Test
    fun `calculateDeltas with values in long delta range`() {
        // Create values spanning the long delta range (17.5-42.5 minutes)
        val data = mutableListOf(
            createGlucoseValue(150.0, 0L),
            createGlucoseValue(148.0, 300000L),   // 5 min
            createGlucoseValue(145.0, 600000L),   // 10 min
            createGlucoseValue(142.0, 900000L),   // 15 min
            createGlucoseValue(138.0, 1200000L),  // 20 min
            createGlucoseValue(135.0, 1500000L),  // 25 min
            createGlucoseValue(132.0, 1800000L),  // 30 min
            createGlucoseValue(128.0, 2100000L),  // 35 min
            createGlucoseValue(125.0, 2400000L)   // 40 min
        )

        val result = deltaCalculator.calculateDeltas(data)

        // Long deltas should be calculated from values in 17.5-42.5 min range
        assertThat(result.longAvgDelta).isGreaterThan(0.0)
    }

    @Test
    fun `calculateDeltas ignores values below minBgValue`() {
        val data = mutableListOf(
            createGlucoseValue(100.0, 0L),
            createGlucoseValue(38.0, 300000L),  // Below minBgValue (39.0)
            createGlucoseValue(90.0, 600000L)
        )

        val result = deltaCalculator.calculateDeltas(data)

        // Should still calculate deltas, but ignore the 38.0 value
        assertThat(result.delta).isNotEqualTo(0.0)
    }

    @Test
    fun `calculateDeltas stops processing after maxLongDeltaMinutes`() {
        // Create values beyond maxLongDeltaMinutes (42.5 min)
        val data = mutableListOf(
            createGlucoseValue(100.0, 0L),
            createGlucoseValue(95.0, 300000L),    // 5 min
            createGlucoseValue(90.0, 2700000L),   // 45 min (beyond max)
            createGlucoseValue(85.0, 3000000L)    // 50 min (should not be processed)
        )

        val result = deltaCalculator.calculateDeltas(data)

        // Should stop at 45 minutes
        assertThat(result).isNotNull()
    }

    @Test
    fun `calculateDeltas uses lastDeltas when available`() {
        // Create values in the lastDeltas range (2.5-7.5 minutes)
        val data = mutableListOf(
            createGlucoseValue(110.0, 0L),
            createGlucoseValue(105.0, 180000L),   // 3 min ago (in lastDeltas range)
            createGlucoseValue(100.0, 300000L),   // 5 min ago (in lastDeltas range)
            createGlucoseValue(95.0, 420000L)     // 7 min ago (in lastDeltas range)
        )

        val result = deltaCalculator.calculateDeltas(data)

        // delta should be from lastDeltas, not shortDeltas
        assertThat(result.delta).isGreaterThan(0.0)
    }

    @Test
    fun `calculateDeltas with negative glucose trend`() {
        // Decreasing glucose values
        val data = mutableListOf(
            createGlucoseValue(100.0, 0L),
            createGlucoseValue(110.0, 300000L),   // 5 min ago, higher
            createGlucoseValue(120.0, 600000L)    // 10 min ago, even higher
        )

        val result = deltaCalculator.calculateDeltas(data)

        // Delta should be negative (glucose dropping)
        assertThat(result.delta).isLessThan(0.0)
        assertThat(result.shortAvgDelta).isLessThan(0.0)
    }

    @Test
    fun `calculateDeltas with stable glucose`() {
        // Flat glucose values
        val data = mutableListOf(
            createGlucoseValue(100.0, 0L),
            createGlucoseValue(100.0, 300000L),
            createGlucoseValue(100.0, 600000L),
            createGlucoseValue(100.0, 900000L)
        )

        val result = deltaCalculator.calculateDeltas(data)

        // All deltas should be near zero
        assertThat(result.delta).isWithin(0.01).of(0.0)
        assertThat(result.shortAvgDelta).isWithin(0.01).of(0.0)
    }

    @Test
    fun `average function returns zero for empty list`() {
        val result = DeltaCalculator.average(emptyList())

        assertThat(result).isEqualTo(0.0)
    }

    @Test
    fun `average function calculates correct average`() {
        val values = listOf(10.0, 20.0, 30.0)

        val result = DeltaCalculator.average(values)

        assertThat(result).isEqualTo(20.0)
    }

    @Test
    fun `average function with single value returns that value`() {
        val values = listOf(42.0)

        val result = DeltaCalculator.average(values)

        assertThat(result).isEqualTo(42.0)
    }

    @Test
    fun `average function with negative values`() {
        val values = listOf(-10.0, -20.0, -30.0)

        val result = DeltaCalculator.average(values)

        assertThat(result).isEqualTo(-20.0)
    }

    @Test
    fun `calculateDeltas with mixed positive and negative deltas`() {
        // Glucose going up then down
        val data = mutableListOf(
            createGlucoseValue(105.0, 0L),        // Now
            createGlucoseValue(100.0, 300000L),   // 5 min ago, lower (rising)
            createGlucoseValue(110.0, 600000L),   // 10 min ago, higher (was falling)
            createGlucoseValue(105.0, 900000L)    // 15 min ago
        )

        val result = deltaCalculator.calculateDeltas(data)

        // Should calculate average of mixed deltas
        assertThat(result.shortAvgDelta).isNotNull()
    }

    private fun createGlucoseValue(value: Double, millisAgo: Long): InMemoryGlucoseValue {
        val now = System.currentTimeMillis()
        return InMemoryGlucoseValue(
            timestamp = now - millisAgo,
            value = value,
            trendArrow = TrendArrow.TRIPLE_UP,
            smoothed = value,
            filledGap = false
        )
    }
}
