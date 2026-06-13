package app.aaps.pump.omnipod.common.bledriver.pod.state

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

/**
 * Tests for [OmnipodDashPodStateManagerImpl.calculateBolusPulseIncrease].
 *
 * This function decides how many of the newly delivered pulses should be attributed to the
 * active bolus vs. basal. Key scenarios:
 *  - No bolus in progress → all pulses attributed to bolus tracker (caller uses them for total)
 *  - Normal bolus delivery → total increase matches expected bolus decrease exactly
 *  - Concurrent basal delivery → total increase > bolus decrease, capped to bolus portion
 *  - Anomaly (fewer total pulses than expected) → returns actual increase and logs warning
 */
class CalculateBolusPulseIncreaseTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config

    private lateinit var sut: OmnipodDashPodStateManagerImpl

    @BeforeEach fun setUp() {
        sut = OmnipodDashPodStateManagerImpl(aapsLogger, rxBus, preferences, config)
    }

    // ---- helpers ------------------------------------------------------------------------------

    private fun increase(
        prevTotal: Int,
        newTotal: Int,
        prevBolusPulsesRemaining: Int?,
        newBolusPulsesRemaining: Int
    ): Short = sut.calculateBolusPulseIncrease(
        previousTotalPulses       = prevTotal.toShort(),
        newTotalPulses            = newTotal.toShort(),
        previousBolusPulsesRemaining = prevBolusPulsesRemaining?.toShort(),
        newBolusPulsesRemaining   = newBolusPulsesRemaining.toShort()
    )

    // ---- no previous bolus tracking -----------------------------------------------------------

    @Test fun `no previous tracking — full pulse increase returned`() {
        // previousBolusPulsesRemaining = null means we have no reference point;
        // return raw increase so the caller can record it
        assertThat(increase(100, 103, null, 0)).isEqualTo(3.toShort())
    }

    @Test fun `no previous tracking, zero increase — returns zero`() {
        assertThat(increase(100, 100, null, 0)).isEqualTo(0.toShort())
    }

    // ---- normal bolus delivery ----------------------------------------------------------------

    @Test fun `normal bolus delivery — increase matches expected bolus decrease`() {
        // 3 total pulses, 3 bolus pulses remaining → 0 now: clean match
        assertThat(increase(100, 103, 3, 0)).isEqualTo(3.toShort())
    }

    @Test fun `bolus still in progress — partial delivery tracked correctly`() {
        // 1 total pulse added, bolus remaining went from 2 → 1: expected 1 bolus pulse
        assertThat(increase(100, 101, 2, 1)).isEqualTo(1.toShort())
    }

    @Test fun `zero increase during active bolus — returns zero`() {
        assertThat(increase(100, 100, 3, 3)).isEqualTo(0.toShort())
    }

    // ---- concurrent basal delivery ------------------------------------------------------------

    @Test fun `concurrent basal — total increase capped to bolus portion`() {
        // 5 total pulses added, but bolus only accounted for 3 → 2 were basal corrections
        // Result must be capped at 3 to avoid over-attributing to bolus
        assertThat(increase(100, 105, 3, 0)).isEqualTo(3.toShort())
    }

    @Test fun `concurrent basal, partial bolus remaining — capped correctly`() {
        // 4 total pulses, bolus remaining went 3 → 1 (2 bolus pulses expected), 2 basal
        assertThat(increase(100, 104, 3, 1)).isEqualTo(2.toShort())
    }

    // ---- anomaly (total < expected) -----------------------------------------------------------

    @Test fun `anomaly — fewer total pulses than expected bolus decrease — returns actual increase`() {
        // Bolus remaining went 3 → 0 (expected 3), but only 2 total pulses added.
        // We return the actual increase (2) and log a warning — do not over-attribute.
        assertThat(increase(100, 102, 3, 0)).isEqualTo(2.toShort())
    }
}
