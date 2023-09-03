package info.nightscout.core.extensions

import info.nightscout.core.main.R
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GlucoseValueExtensionKtTest : TestBaseWithProfile() {

    private val glucoseValue = GlucoseValue(raw = 0.0, noise = 0.0, value = 100.0, timestamp = 1514766900000, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT)
    private val inMemoryGlucoseValue = InMemoryGlucoseValue(1000, 100.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN)
    @Test
    fun valueToUnitsString() {
        Assertions.assertEquals("100", glucoseValue.valueToUnitsString(GlucoseUnit.MGDL))
        Assertions.assertEquals("5.6", glucoseValue.valueToUnitsString(GlucoseUnit.MMOL))
    }
    @Test
    fun inMemoryValueToUnitsString() {
        Assertions.assertEquals("100", inMemoryGlucoseValue.valueToUnitsString(GlucoseUnit.MGDL))
        Assertions.assertEquals("5.6", inMemoryGlucoseValue.valueToUnitsString(GlucoseUnit.MMOL))
    }
    @Test
    fun inMemoryValueToUnits() {
        Assertions.assertEquals(100.0, inMemoryGlucoseValue.valueToUnits(GlucoseUnit.MGDL))
        Assertions.assertEquals(5.55, inMemoryGlucoseValue.valueToUnits(GlucoseUnit.MMOL), 0.01)
    }
    @Test
    fun directionToIcon() {
        Assertions.assertEquals(R.drawable.ic_flat, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.NONE
        Assertions.assertEquals(R.drawable.ic_invalid, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.TRIPLE_DOWN
        Assertions.assertEquals(R.drawable.ic_invalid, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.TRIPLE_UP
        Assertions.assertEquals(R.drawable.ic_invalid, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.DOUBLE_DOWN
        Assertions.assertEquals(R.drawable.ic_doubledown, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.SINGLE_DOWN
        Assertions.assertEquals(R.drawable.ic_singledown, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.FORTY_FIVE_DOWN
        Assertions.assertEquals(R.drawable.ic_fortyfivedown, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.FORTY_FIVE_UP
        Assertions.assertEquals(R.drawable.ic_fortyfiveup, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.SINGLE_UP
        Assertions.assertEquals(R.drawable.ic_singleup, glucoseValue.trendArrow.directionToIcon())
        glucoseValue.trendArrow = GlucoseValue.TrendArrow.DOUBLE_UP
        Assertions.assertEquals(R.drawable.ic_doubleup, glucoseValue.trendArrow.directionToIcon())
    }
}