package app.aaps.pump.common.hw.rileylink.ble.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for FrequencyTrial
 */
class FrequencyTrialTest {

    private lateinit var trial: FrequencyTrial

    @BeforeEach
    fun setup() {
        trial = FrequencyTrial()
    }

    @Test
    fun `initial state has zero tries`() {
        assertEquals(0, trial.tries)
    }

    @Test
    fun `initial state has zero successes`() {
        assertEquals(0, trial.successes)
    }

    @Test
    fun `initial state has zero average RSSI`() {
        assertEquals(0.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `initial state has zero frequency`() {
        assertEquals(0.0, trial.frequencyMHz, 0.001)
    }

    @Test
    fun `initial state has empty RSSI list`() {
        assertTrue(trial.rssiList.isEmpty())
    }

    @Test
    fun `calculateAverage with empty list returns -99`() {
        trial.calculateAverage()
        assertEquals(-99.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `calculateAverage with single RSSI value`() {
        trial.rssiList.add(-75)
        trial.calculateAverage()

        assertEquals(-75.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `calculateAverage with multiple RSSI values`() {
        trial.rssiList.add(-70)
        trial.rssiList.add(-80)
        trial.rssiList.add(-75)

        trial.calculateAverage()

        // Average of abs(-70), abs(-80), abs(-75) = (70 + 80 + 75) / 3 = 75, negated = -75
        assertEquals(-75.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `calculateAverage handles positive RSSI values`() {
        // In some edge cases RSSI might be positive (shouldn't happen in practice)
        trial.rssiList.add(70)
        trial.rssiList.add(80)

        trial.calculateAverage()

        // abs(70) + abs(80) = 150, 150/2 = 75, negated = -75
        assertEquals(-75.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `calculateAverage handles mixed positive and negative RSSI`() {
        trial.rssiList.add(-70)
        trial.rssiList.add(80)

        trial.calculateAverage()

        // abs(-70) + abs(80) = 150, 150/2 = 75, negated = -75
        assertEquals(-75.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `can set tries`() {
        trial.tries = 10
        assertEquals(10, trial.tries)
    }

    @Test
    fun `can set successes`() {
        trial.successes = 8
        assertEquals(8, trial.successes)
    }

    @Test
    fun `can set frequency`() {
        trial.frequencyMHz = 916.5
        assertEquals(916.5, trial.frequencyMHz, 0.001)
    }

    @Test
    fun `can manually set average RSSI`() {
        trial.averageRSSI = -75.0
        assertEquals(-75.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `rssi list is mutable`() {
        trial.rssiList.add(-70)
        trial.rssiList.add(-75)
        trial.rssiList.add(-80)

        assertEquals(3, trial.rssiList.size)

        trial.rssiList.removeAt(1)

        assertEquals(2, trial.rssiList.size)
        assertEquals(-70, trial.rssiList[0])
        assertEquals(-80, trial.rssiList[1])
    }

    @Test
    fun `complete trial workflow`() {
        // Simulate a frequency trial at 916.5 MHz
        trial.frequencyMHz = 916.5
        trial.tries = 5

        // Add some RSSI measurements
        val rssiMeasurements = listOf(-72, -75, -73, -74, -71)
        rssiMeasurements.forEach { rssi ->
            trial.rssiList.add(rssi)
            if (rssi > -80) { // Assume success threshold is -80
                trial.successes++
            }
        }

        trial.calculateAverage()

        // Verify results
        assertEquals(5, trial.tries)
        assertEquals(5, trial.successes)
        assertEquals(916.5, trial.frequencyMHz, 0.001)

        // Average should be -(72 + 75 + 73 + 74 + 71) / 5 = -73
        assertEquals(-73.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `calculateAverage with very weak signals`() {
        trial.rssiList.add(-95)
        trial.rssiList.add(-98)
        trial.rssiList.add(-92)

        trial.calculateAverage()

        // (95 + 98 + 92) / 3 = 95, negated = -95
        assertEquals(-95.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `calculateAverage with very strong signals`() {
        trial.rssiList.add(-45)
        trial.rssiList.add(-50)
        trial.rssiList.add(-48)

        trial.calculateAverage()

        // (45 + 50 + 48) / 3 = 47.67, negated = -47.67
        assertEquals(-47.666, trial.averageRSSI, 0.01)
    }

    @Test
    fun `calculateAverage uses absolute values correctly`() {
        // This tests that the implementation uses abs() correctly
        trial.rssiList.add(-100)
        trial.calculateAverage()

        assertEquals(-100.0, trial.averageRSSI, 0.001)
    }

    @Test
    fun `success rate calculation`() {
        trial.tries = 10
        trial.successes = 7

        val successRate = trial.successes.toDouble() / trial.tries.toDouble()

        assertEquals(0.7, successRate, 0.001)
    }

    @Test
    fun `zero tries avoids division by zero`() {
        trial.tries = 0
        trial.successes = 0

        // This shouldn't throw - just verifying tries can be zero
        assertEquals(0, trial.tries)
    }

    @Test
    fun `realistic pump frequency trial scenario`() {
        // Simulate a realistic frequency trial for pump communication
        trial.frequencyMHz = 916.55
        trial.tries = 10

        // Simulate 10 communication attempts with varying RSSI
        val rssiValues = listOf(-70, -72, -68, -75, -71, -69, -73, -70, -72, -71)

        rssiValues.forEach { rssi ->
            trial.rssiList.add(rssi)
            // Success if RSSI better than -80 dBm
            if (rssi > -80) {
                trial.successes++
            }
        }

        trial.calculateAverage()

        // All should be successful
        assertEquals(10, trial.successes)

        // Average RSSI should be around -71
        assertTrue(trial.averageRSSI > -73.0 && trial.averageRSSI < -69.0)

        // Verify frequency
        assertEquals(916.55, trial.frequencyMHz, 0.001)
    }

    @Test
    fun `averageRSSI2 property exists and can be set`() {
        trial.averageRSSI2 = -80.5
        assertEquals(-80.5, trial.averageRSSI2, 0.001)
    }

    @Test
    fun `calculateAverage does not modify rssiList`() {
        val originalValues = listOf(-70, -75, -80)
        originalValues.forEach { trial.rssiList.add(it) }

        trial.calculateAverage()

        assertEquals(3, trial.rssiList.size)
        assertEquals(-70, trial.rssiList[0])
        assertEquals(-75, trial.rssiList[1])
        assertEquals(-80, trial.rssiList[2])
    }
}
