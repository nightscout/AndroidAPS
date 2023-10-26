package app.aaps.plugins.main.extensions

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.objects.R
import app.aaps.core.objects.extensions.directionToIcon
import app.aaps.core.objects.extensions.valueToUnits
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GlucoseValueExtensionKtTest : TestBaseWithProfile() {

    private val glucoseValue =
        GV(raw = 0.0, noise = 0.0, value = 100.0, timestamp = 1514766900000, sourceSensor = SourceSensor.UNKNOWN, trendArrow = TrendArrow.FLAT)
    private val inMemoryGlucoseValue = InMemoryGlucoseValue(1000, 100.0, sourceSensor = SourceSensor.UNKNOWN)

    @Test
    fun valueToUnitsString() {
    }

    @Test
    fun inMemoryValueToUnits() {
        assertThat(inMemoryGlucoseValue.valueToUnits(GlucoseUnit.MGDL)).isEqualTo(100.0)
        assertThat(inMemoryGlucoseValue.valueToUnits(GlucoseUnit.MMOL)).isWithin(0.01).of(5.55)
    }

    @Test
    fun directionToIcon() {
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_flat)
        glucoseValue.trendArrow = TrendArrow.NONE
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_invalid)
        glucoseValue.trendArrow = TrendArrow.TRIPLE_DOWN
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_invalid)
        glucoseValue.trendArrow = TrendArrow.TRIPLE_UP
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_invalid)
        glucoseValue.trendArrow = TrendArrow.DOUBLE_DOWN
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_doubledown)
        glucoseValue.trendArrow = TrendArrow.SINGLE_DOWN
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_singledown)
        glucoseValue.trendArrow = TrendArrow.FORTY_FIVE_DOWN
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_fortyfivedown)
        glucoseValue.trendArrow = TrendArrow.FORTY_FIVE_UP
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_fortyfiveup)
        glucoseValue.trendArrow = TrendArrow.SINGLE_UP
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_singleup)
        glucoseValue.trendArrow = TrendArrow.DOUBLE_UP
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_doubleup)
    }
}
