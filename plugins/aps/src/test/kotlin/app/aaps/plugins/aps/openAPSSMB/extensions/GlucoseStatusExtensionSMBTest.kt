package app.aaps.plugins.aps.openAPSSMB.extensions

import app.aaps.core.interfaces.aps.GlucoseStatusSMB
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class GlucoseStatusExtensionSMBTest : TestBase() {

    @Mock lateinit var decimalFormatter: DecimalFormatter

    @BeforeEach
    fun setup() {
        whenever(decimalFormatter.to0Decimal(any())).thenAnswer {
            String.format("%.0f", it.arguments[0] as Double)
        }
        whenever(decimalFormatter.to2Decimal(any())).thenAnswer {
            String.format("%.2f", it.arguments[0] as Double)
        }
    }

    @Test
    fun `log formats glucose status with all fields`() {
        val status = GlucoseStatusSMB(
            glucose = 120.0,
            noise = 5.0,
            delta = -3.0,
            shortAvgDelta = -2.5,
            longAvgDelta = -1.8,
            date = 1609459200000L
        )

        val log = status.log(decimalFormatter)

        assertThat(log).contains("Glucose: 120 mg/dl")
        assertThat(log).contains("Noise: 5")
        assertThat(log).contains("Delta: -3 mg/dl")
        assertThat(log).contains("Short avg. delta:  -2.50 mg/dl")
        assertThat(log).contains("Long avg. delta: -1.80 mg/dl")
    }

    @Test
    fun `log formats glucose with zero values`() {
        val status = GlucoseStatusSMB(
            glucose = 0.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L
        )

        val log = status.log(decimalFormatter)

        assertThat(log).contains("Glucose: 0 mg/dl")
        assertThat(log).contains("Delta: 0 mg/dl")
        assertThat(log).contains("Short avg. delta:  0.00 mg/dl")
    }

    @Test
    fun `log formats glucose with high values`() {
        val status = GlucoseStatusSMB(
            glucose = 400.0,
            noise = 10.0,
            delta = 15.0,
            shortAvgDelta = 12.5,
            longAvgDelta = 10.8,
            date = 1609459200000L
        )

        val log = status.log(decimalFormatter)

        assertThat(log).contains("Glucose: 400 mg/dl")
        assertThat(log).contains("Delta: 15 mg/dl")
    }

    @Test
    fun `log formats glucose with negative delta`() {
        val status = GlucoseStatusSMB(
            glucose = 100.0,
            noise = 0.0,
            delta = -8.5,
            shortAvgDelta = -7.2,
            longAvgDelta = -5.3,
            date = 1609459200000L
        )

        val log = status.log(decimalFormatter)

        assertThat(log).contains("Delta: -9 mg/dl")
        assertThat(log).contains("Short avg. delta:  -7.20 mg/dl")
        assertThat(log).contains("Long avg. delta: -5.30 mg/dl")
    }

    @Test
    fun `asRounded rounds glucose to 0_1`() {
        val status = GlucoseStatusSMB(
            glucose = 120.456,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(120.5)
    }

    @Test
    fun `asRounded rounds noise to 0_01`() {
        val status = GlucoseStatusSMB(
            glucose = 120.0,
            noise = 5.678,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.noise).isEqualTo(5.68)
    }

    @Test
    fun `asRounded rounds delta to 0_01`() {
        val status = GlucoseStatusSMB(
            glucose = 120.0,
            noise = 0.0,
            delta = 3.456,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.delta).isEqualTo(3.46)
    }

    @Test
    fun `asRounded rounds shortAvgDelta to 0_01`() {
        val status = GlucoseStatusSMB(
            glucose = 120.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 2.789,
            longAvgDelta = 0.0,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.shortAvgDelta).isEqualTo(2.79)
    }

    @Test
    fun `asRounded rounds longAvgDelta to 0_01`() {
        val status = GlucoseStatusSMB(
            glucose = 120.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 1.234,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.longAvgDelta).isEqualTo(1.23)
    }

    @Test
    fun `asRounded preserves date`() {
        val status = GlucoseStatusSMB(
            glucose = 120.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.date).isEqualTo(1609459200000L)
    }

    @Test
    fun `asRounded handles all values together`() {
        val status = GlucoseStatusSMB(
            glucose = 123.456,
            noise = 7.891,
            delta = -4.567,
            shortAvgDelta = -3.234,
            longAvgDelta = -2.111,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(123.5)
        assertThat(rounded.noise).isEqualTo(7.89)
        assertThat(rounded.delta).isEqualTo(-4.57)
        assertThat(rounded.shortAvgDelta).isEqualTo(-3.23)
        assertThat(rounded.longAvgDelta).isEqualTo(-2.11)
        assertThat(rounded.date).isEqualTo(1609459200000L)
    }

    @Test
    fun `asRounded handles boundary rounding cases`() {
        val status = GlucoseStatusSMB(
            glucose = 120.05,  // rounds to 120.1
            noise = 5.005,      // rounds to 5.01
            delta = 3.005,      // rounds to 3.01
            shortAvgDelta = 2.005,
            longAvgDelta = 1.005,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(120.1)
        assertThat(rounded.noise).isEqualTo(5.01)
        assertThat(rounded.delta).isEqualTo(3.01)
    }

    @Test
    fun `asRounded handles negative values`() {
        val status = GlucoseStatusSMB(
            glucose = 80.456,
            noise = 0.0,
            delta = -5.678,
            shortAvgDelta = -4.567,
            longAvgDelta = -3.456,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(80.5)
        assertThat(rounded.delta).isEqualTo(-5.68)
        assertThat(rounded.shortAvgDelta).isEqualTo(-4.57)
        assertThat(rounded.longAvgDelta).isEqualTo(-3.46)
    }

    @Test
    fun `asRounded handles very small values`() {
        val status = GlucoseStatusSMB(
            glucose = 0.123,
            noise = 0.001,
            delta = 0.001,
            shortAvgDelta = 0.001,
            longAvgDelta = 0.001,
            date = 1609459200000L
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(0.1)
        assertThat(rounded.noise).isEqualTo(0.0)
        assertThat(rounded.delta).isEqualTo(0.0)
    }

    @Test
    fun `log handles fractional values with proper formatting`() {
        val status = GlucoseStatusSMB(
            glucose = 125.5,
            noise = 3.25,
            delta = -2.75,
            shortAvgDelta = -1.5,
            longAvgDelta = -0.8,
            date = 1609459200000L
        )

        val log = status.log(decimalFormatter)

        assertThat(log).contains("Glucose: 126 mg/dl")  // rounds to 0 decimal
        assertThat(log).contains("Delta: -3 mg/dl")     // rounds to 0 decimal
        assertThat(log).contains("Short avg. delta:  -1.50 mg/dl")  // 2 decimals
        assertThat(log).contains("Long avg. delta: -0.80 mg/dl")    // 2 decimals
    }
}
