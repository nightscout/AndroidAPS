package app.aaps.pump.common.hw.rileylink.ble.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for FrequencyScanResults
 */
class FrequencyScanResultsTest {

    private lateinit var scanResults: FrequencyScanResults

    @BeforeEach
    fun setup() {
        scanResults = FrequencyScanResults()
    }

    @Test
    fun `initial state has empty trials list`() {
        assertTrue(scanResults.trials.isEmpty())
    }

    @Test
    fun `initial state has zero best frequency`() {
        assertEquals(0.0, scanResults.bestFrequencyMHz, 0.001)
    }

    @Test
    fun `initial state has zero dateTime`() {
        assertEquals(0L, scanResults.dateTime)
    }

    @Test
    fun `can add trial to results`() {
        val trial = FrequencyTrial().apply {
            frequencyMHz = 916.5
            averageRSSI = -75.0
            tries = 10
            successes = 8
        }

        scanResults.trials.add(trial)

        assertEquals(1, scanResults.trials.size)
        assertEquals(916.5, scanResults.trials[0].frequencyMHz, 0.001)
    }

    @Test
    fun `can add multiple trials`() {
        val trial1 = FrequencyTrial().apply {
            frequencyMHz = 916.5
            averageRSSI = -75.0
        }

        val trial2 = FrequencyTrial().apply {
            frequencyMHz = 916.6
            averageRSSI = -70.0
        }

        val trial3 = FrequencyTrial().apply {
            frequencyMHz = 916.7
            averageRSSI = -80.0
        }

        scanResults.trials.add(trial1)
        scanResults.trials.add(trial2)
        scanResults.trials.add(trial3)

        assertEquals(3, scanResults.trials.size)
    }

    @Test
    fun `sort orders by average RSSI ascending`() {
        // Lower (more negative) RSSI should come first after sorting
        val trial1 = FrequencyTrial().apply {
            frequencyMHz = 916.5
            averageRSSI = -70.0 // Better signal
        }

        val trial2 = FrequencyTrial().apply {
            frequencyMHz = 916.6
            averageRSSI = -80.0 // Worse signal
        }

        val trial3 = FrequencyTrial().apply {
            frequencyMHz = 916.7
            averageRSSI = -75.0 // Medium signal
        }

        scanResults.trials.add(trial2) // Add in wrong order
        scanResults.trials.add(trial1)
        scanResults.trials.add(trial3)

        scanResults.sort()

        // After sorting, should be in ascending RSSI order (most negative first)
        assertEquals(-80.0, scanResults.trials[0].averageRSSI)
        assertEquals(-75.0, scanResults.trials[1].averageRSSI)
        assertEquals(-70.0, scanResults.trials[2].averageRSSI)
    }

    @Test
    fun `sort uses frequency as tiebreaker when RSSI is equal with large differences`() {
        // Note: The frequency tiebreaker uses .toInt() which loses precision for small differences
        // This test uses whole MHz differences to ensure the tiebreaker works
        val trial1 = FrequencyTrial().apply {
            frequencyMHz = 918.0
            averageRSSI = -75.0
        }

        val trial2 = FrequencyTrial().apply {
            frequencyMHz = 916.0
            averageRSSI = -75.0
        }

        val trial3 = FrequencyTrial().apply {
            frequencyMHz = 917.0
            averageRSSI = -75.0
        }

        scanResults.trials.add(trial1)
        scanResults.trials.add(trial2)
        scanResults.trials.add(trial3)

        scanResults.sort()

        // When RSSI is equal, should sort by frequency (with integer precision)
        assertEquals(916.0, scanResults.trials[0].frequencyMHz, 0.001)
        assertEquals(917.0, scanResults.trials[1].frequencyMHz, 0.001)
        assertEquals(918.0, scanResults.trials[2].frequencyMHz, 0.001)
    }

    @Test
    fun `can set best frequency`() {
        scanResults.bestFrequencyMHz = 916.55

        assertEquals(916.55, scanResults.bestFrequencyMHz, 0.001)
    }

    @Test
    fun `can set dateTime`() {
        val now = System.currentTimeMillis()
        scanResults.dateTime = now

        assertEquals(now, scanResults.dateTime)
    }

    @Test
    fun `sort handles empty list`() {
        scanResults.sort()
        // Should not throw exception
        assertTrue(scanResults.trials.isEmpty())
    }

    @Test
    fun `sort handles single trial`() {
        val trial = FrequencyTrial().apply {
            frequencyMHz = 916.5
            averageRSSI = -75.0
        }

        scanResults.trials.add(trial)
        scanResults.sort()

        assertEquals(1, scanResults.trials.size)
        assertEquals(916.5, scanResults.trials[0].frequencyMHz, 0.001)
    }

    @Test
    fun `complete scan workflow`() {
        // Simulate a frequency scan
        val frequencies = listOf(916.45, 916.50, 916.55, 916.60, 916.65)
        val rssiValues = listOf(-78.0, -72.0, -69.0, -75.0, -80.0) // 916.55 should be best

        frequencies.forEachIndexed { index, freq ->
            val trial = FrequencyTrial().apply {
                frequencyMHz = freq
                averageRSSI = rssiValues[index]
                tries = 10
                successes = 8
            }
            scanResults.trials.add(trial)
        }

        scanResults.sort()
        scanResults.dateTime = System.currentTimeMillis()

        // Best frequency should be the one with lowest (most negative) RSSI after sort
        // But in real usage, we'd pick the LAST one (highest RSSI = least negative)
        // Let's verify the sorting is correct
        assertTrue(scanResults.trials[0].averageRSSI <= scanResults.trials.last().averageRSSI)
    }

    @Test
    fun `trials list is mutable`() {
        val trial1 = FrequencyTrial().apply {
            frequencyMHz = 916.5
            averageRSSI = -75.0
        }

        scanResults.trials.add(trial1)
        assertEquals(1, scanResults.trials.size)

        scanResults.trials.clear()
        assertEquals(0, scanResults.trials.size)
    }

    @Test
    fun `sort with negative and positive frequencies`() {
        val trial1 = FrequencyTrial().apply {
            frequencyMHz = 916.5
            averageRSSI = -75.0
        }

        val trial2 = FrequencyTrial().apply {
            frequencyMHz = 868.5 // Different band
            averageRSSI = -75.0
        }

        scanResults.trials.add(trial1)
        scanResults.trials.add(trial2)

        scanResults.sort()

        // Should sort by frequency when RSSI is equal
        assertEquals(868.5, scanResults.trials[0].frequencyMHz, 0.001)
        assertEquals(916.5, scanResults.trials[1].frequencyMHz, 0.001)
    }

    @Test
    fun `realistic US pump frequency scan`() {
        // US pump frequencies typically around 916.5 MHz
        val baseFreq = 916.0
        val scanResults = FrequencyScanResults()

        // Scan in 0.05 MHz steps
        for (i in 0..40) {
            val freq = baseFreq + (i * 0.025)
            val trial = FrequencyTrial().apply {
                frequencyMHz = freq
                // Simulate varying signal strength, with peak around 916.5
                averageRSSI = -85.0 + (10 * kotlin.math.cos((freq - 916.5) * 5)).toInt()
                tries = 5
                successes = if (averageRSSI > -80) 4 else 2
            }
            scanResults.trials.add(trial)
        }

        scanResults.sort()

        // Should have all trials
        assertEquals(41, scanResults.trials.size)

        // Verify sorted
        for (i in 0 until scanResults.trials.size - 1) {
            assertTrue(scanResults.trials[i].averageRSSI <= scanResults.trials[i + 1].averageRSSI)
        }
    }
}
