package app.aaps.plugins.main.extensions

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.InMemoryGlucoseValue
import app.aaps.core.main.R
import app.aaps.core.main.extensions.directionToIcon
import app.aaps.core.main.extensions.valueToUnits
import app.aaps.database.entities.GlucoseValue
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class GlucoseValueExtensionKtTest : TestBaseWithProfile() {

    private val glucoseValue =
        GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = 1514766900000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT)
    private val inMemoryGlucoseValue = InMemoryGlucoseValue(1000, 100.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN)

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
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.NONE
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_invalid)
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.TRIPLE_DOWN
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_invalid)
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.TRIPLE_UP
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_invalid)
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.DOUBLE_DOWN
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_doubledown)
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.SINGLE_DOWN
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_singledown)
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_fortyfivedown)
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.FORTY_FIVE_UP
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_fortyfiveup)
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.SINGLE_UP
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_singleup)
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.DOUBLE_UP
        assertThat(glucoseValue.trendArrow.directionToIcon()).isEqualTo(R.drawable.ic_doubleup)
    }
}
