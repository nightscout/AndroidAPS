package app.aaps.plugins.aps.openAPSAutoISF.extensions

import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class GlucoseStatusExtensionTest : TestBase() {

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
        val status = GlucoseStatusAutoIsf(
            glucose = 120.0,
            noise = 5.0,
            delta = -3.0,
            shortAvgDelta = -2.5,
            longAvgDelta = -1.8,
            date = 1609459200000L,
            duraISFminutes = 15.0,
            duraISFaverage = 118.5,
            parabolaMinutes = 30.0,
            deltaPl = -2.0,
            deltaPn = -1.5,
            bgAcceleration = 0.5,
            a0 = 120.0,
            a1 = -2.0,
            a2 = 0.1,
            corrSqu = 0.95
        )

        val log = status.log(decimalFormatter)

        assertThat(log).contains("Glucose: 120 mg/dl")
        assertThat(log).contains("Noise: 5")
        assertThat(log).contains("Delta: -3 mg/dl")
        assertThat(log).contains("Short avg. delta:  -2.50 mg/dl")
        assertThat(log).contains("Long avg. delta: -1.80 mg/dl")
        assertThat(log).contains("Dura ISF minutes: 15.00 m")
        assertThat(log).contains("Dura ISF average: 118.50 mg/dl")
        assertThat(log).contains("Parabola minutes: 30.00 m")
        assertThat(log).contains("Parabola correlation: 0.95")
        assertThat(log).contains("Parabola fit a0: 120.00 mg/dl")
        assertThat(log).contains("Parabola fit a1: -2.00 mg/dl/5m")
        assertThat(log).contains("Parabola fit a2: 0.10 mg/dl/(5m)^2")
    }

    @Test
    fun `asRounded rounds glucose to 0_1`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.456,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L,
            duraISFminutes = 0.0,
            duraISFaverage = 0.0,
            parabolaMinutes = 0.0,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 0.0,
            a1 = 0.0,
            a2 = 0.0,
            corrSqu = 0.0
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(120.5)
    }

    @Test
    fun `asRounded rounds noise to 0_01`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.0,
            noise = 5.678,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L,
            duraISFminutes = 0.0,
            duraISFaverage = 0.0,
            parabolaMinutes = 0.0,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 0.0,
            a1 = 0.0,
            a2 = 0.0,
            corrSqu = 0.0
        )

        val rounded = status.asRounded()

        assertThat(rounded.noise).isEqualTo(5.68)
    }

    @Test
    fun `asRounded rounds delta fields to 0_01`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.0,
            noise = 0.0,
            delta = 3.456,
            shortAvgDelta = 2.789,
            longAvgDelta = 1.234,
            date = 1609459200000L,
            duraISFminutes = 0.0,
            duraISFaverage = 0.0,
            parabolaMinutes = 0.0,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 0.0,
            a1 = 0.0,
            a2 = 0.0,
            corrSqu = 0.0
        )

        val rounded = status.asRounded()

        assertThat(rounded.delta).isEqualTo(3.46)
        assertThat(rounded.shortAvgDelta).isEqualTo(2.79)
        assertThat(rounded.longAvgDelta).isEqualTo(1.23)
    }

    @Test
    fun `asRounded rounds duraISF fields correctly`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L,
            duraISFminutes = 15.456,
            duraISFaverage = 118.567,
            parabolaMinutes = 0.0,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 0.0,
            a1 = 0.0,
            a2 = 0.0,
            corrSqu = 0.0
        )

        val rounded = status.asRounded()

        assertThat(rounded.duraISFminutes).isEqualTo(15.5)  // 0.1 precision
        assertThat(rounded.duraISFaverage).isEqualTo(118.6) // 0.1 precision
    }

    @Test
    fun `asRounded rounds parabolaMinutes to 0_1`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L,
            duraISFminutes = 0.0,
            duraISFaverage = 0.0,
            parabolaMinutes = 30.789,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 0.0,
            a1 = 0.0,
            a2 = 0.0,
            corrSqu = 0.0
        )

        val rounded = status.asRounded()

        assertThat(rounded.parabolaMinutes).isEqualTo(30.8)
    }

    @Test
    fun `asRounded rounds corrSqu to 0_0001`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L,
            duraISFminutes = 0.0,
            duraISFaverage = 0.0,
            parabolaMinutes = 0.0,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 0.0,
            a1 = 0.0,
            a2 = 0.0,
            corrSqu = 0.956789
        )

        val rounded = status.asRounded()

        assertThat(rounded.corrSqu).isEqualTo(0.9568)
    }

    @Test
    fun `asRounded rounds parabola coefficients correctly`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L,
            duraISFminutes = 0.0,
            duraISFaverage = 0.0,
            parabolaMinutes = 0.0,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 120.567,
            a1 = -2.345,
            a2 = 0.123,
            corrSqu = 0.0
        )

        val rounded = status.asRounded()

        assertThat(rounded.a0).isEqualTo(120.6)  // 0.1 precision
        assertThat(rounded.a1).isEqualTo(-2.35)  // 0.01 precision
        assertThat(rounded.a2).isEqualTo(0.12)   // 0.01 precision
    }

    @Test
    fun `asRounded preserves date`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L,
            duraISFminutes = 0.0,
            duraISFaverage = 0.0,
            parabolaMinutes = 0.0,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 0.0,
            a1 = 0.0,
            a2 = 0.0,
            corrSqu = 0.0
        )

        val rounded = status.asRounded()

        assertThat(rounded.date).isEqualTo(1609459200000L)
    }

    @Test
    fun `asRounded handles all values together`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 123.456,
            noise = 7.891,
            delta = -4.567,
            shortAvgDelta = -3.234,
            longAvgDelta = -2.111,
            date = 1609459200000L,
            duraISFminutes = 15.678,
            duraISFaverage = 122.345,
            parabolaMinutes = 32.123,
            deltaPl = -2.567,
            deltaPn = -1.234,
            bgAcceleration = 0.567,
            a0 = 123.789,
            a1 = -2.678,
            a2 = 0.134,
            corrSqu = 0.98765
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(123.5)
        assertThat(rounded.noise).isEqualTo(7.89)
        assertThat(rounded.delta).isEqualTo(-4.57)
        assertThat(rounded.shortAvgDelta).isEqualTo(-3.23)
        assertThat(rounded.longAvgDelta).isEqualTo(-2.11)
        assertThat(rounded.duraISFminutes).isEqualTo(15.7)
        assertThat(rounded.duraISFaverage).isEqualTo(122.3)
        assertThat(rounded.parabolaMinutes).isEqualTo(32.1)
        assertThat(rounded.a0).isEqualTo(123.8)
        assertThat(rounded.a1).isEqualTo(-2.68)
        assertThat(rounded.a2).isEqualTo(0.13)
        assertThat(rounded.corrSqu).isEqualTo(0.9877)
        assertThat(rounded.date).isEqualTo(1609459200000L)
    }

    @Test
    fun `log handles zero values`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 0.0,
            noise = 0.0,
            delta = 0.0,
            shortAvgDelta = 0.0,
            longAvgDelta = 0.0,
            date = 1609459200000L,
            duraISFminutes = 0.0,
            duraISFaverage = 0.0,
            parabolaMinutes = 0.0,
            deltaPl = 0.0,
            deltaPn = 0.0,
            bgAcceleration = 0.0,
            a0 = 0.0,
            a1 = 0.0,
            a2 = 0.0,
            corrSqu = 0.0
        )

        val log = status.log(decimalFormatter)

        assertThat(log).contains("Glucose: 0 mg/dl")
        assertThat(log).contains("Delta: 0 mg/dl")
        assertThat(log).contains("Dura ISF minutes: 0.00 m")
        assertThat(log).contains("Parabola correlation: 0.00")
    }

    @Test
    fun `log handles negative values`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 80.0,
            noise = 0.0,
            delta = -8.5,
            shortAvgDelta = -7.2,
            longAvgDelta = -5.3,
            date = 1609459200000L,
            duraISFminutes = 20.0,
            duraISFaverage = 85.0,
            parabolaMinutes = 25.0,
            deltaPl = -6.0,
            deltaPn = -4.5,
            bgAcceleration = -0.3,
            a0 = 82.0,
            a1 = -5.0,
            a2 = -0.2,
            corrSqu = 0.92
        )

        val log = status.log(decimalFormatter)

        assertThat(log).contains("Delta: -9 mg/dl")
        assertThat(log).contains("Short avg. delta:  -7.20 mg/dl")
        assertThat(log).contains("Parabola fit a1: -5.00 mg/dl/5m")
        assertThat(log).contains("Parabola fit a2: -0.20 mg/dl/(5m)^2")
    }

    @Test
    fun `asRounded handles boundary rounding cases`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 120.06,       // rounds to 120.1
            noise = 5.006,          // rounds to 5.01
            delta = 3.006,          // rounds to 3.01
            shortAvgDelta = 2.006,
            longAvgDelta = 1.006,
            date = 1609459200000L,
            duraISFminutes = 15.06, // rounds to 15.1
            duraISFaverage = 118.06,
            parabolaMinutes = 30.06,
            deltaPl = -2.006,
            deltaPn = -1.006,
            bgAcceleration = 0.506,
            a0 = 120.06,
            a1 = -2.006,
            a2 = 0.106,
            corrSqu = 0.95006       // rounds to 0.9501
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(120.1)
        assertThat(rounded.noise).isEqualTo(5.01)
        assertThat(rounded.delta).isEqualTo(3.01)
        assertThat(rounded.duraISFminutes).isEqualTo(15.1)
        assertThat(rounded.corrSqu).isEqualTo(0.9501)
    }

    @Test
    fun `asRounded handles very small values`() {
        val status = GlucoseStatusAutoIsf(
            glucose = 0.123,
            noise = 0.001,
            delta = 0.001,
            shortAvgDelta = 0.001,
            longAvgDelta = 0.001,
            date = 1609459200000L,
            duraISFminutes = 0.01,
            duraISFaverage = 0.01,
            parabolaMinutes = 0.01,
            deltaPl = 0.001,
            deltaPn = 0.001,
            bgAcceleration = 0.001,
            a0 = 0.01,
            a1 = 0.001,
            a2 = 0.001,
            corrSqu = 0.00001
        )

        val rounded = status.asRounded()

        assertThat(rounded.glucose).isEqualTo(0.1)
        assertThat(rounded.noise).isEqualTo(0.0)
        assertThat(rounded.delta).isEqualTo(0.0)
        assertThat(rounded.corrSqu).isEqualTo(0.0)
    }
}
