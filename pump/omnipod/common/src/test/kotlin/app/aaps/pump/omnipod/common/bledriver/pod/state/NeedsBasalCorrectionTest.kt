package app.aaps.pump.omnipod.common.bledriver.pod.state

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

/**
 * Tests for [OmnipodDashPodStateManagerImpl.needsBasalCorrection].
 *
 * State is set up via [OmnipodDashPodStateManagerImpl.podState] (internal), which avoids the
 * complexity of building full response byte arrays while keeping assertions precise.
 *
 * Drift arithmetic (POD_PULSE_BOLUS_UNITS = 0.05 U/pulse):
 *   basalDelivered  = (pulsesDelivered − bolusPulses) × 0.05
 *   basalDrift      = basalDelivered − basalExpected
 *   correctionThreshold = −0.025 U  (half a pulse under-delivery)
 *   reset boundary      = ±0.10 U   (two full pulses)
 */
class NeedsBasalCorrectionTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config

    private lateinit var sut: OmnipodDashPodStateManagerImpl

    // ---- setup --------------------------------------------------------------------------------

    @BeforeEach fun setUp() {
        `when`(config.enableOmnipodDriftCompensation()).thenReturn(true)
        sut = OmnipodDashPodStateManagerImpl(aapsLogger, rxBus, preferences, config)
        sut.activationProgress = ActivationProgress.COMPLETED
    }

    // ---- helpers ------------------------------------------------------------------------------

    /**
     * Configures pulse counters and basalExpected to produce a known drift:
     *   drift = (totalPulses − bolusPulses) × 0.05 − expectedUnits
     */
    private fun setDrift(totalPulses: Int, bolusPulses: Int, expectedUnits: Double) {
        sut.podState.pulsesDelivered = totalPulses.toShort()
        sut.podState.bolusPulsesDelivered = bolusPulses.toShort()
        sut.podState.basalExpected = expectedUnits
    }

    // ---- prerequisite checks ------------------------------------------------------------------

    @Test fun `drift compensation disabled — returns false`() {
        `when`(config.enableOmnipodDriftCompensation()).thenReturn(false)
        setDrift(10, 0, 0.0)    // drift = +0.5, would reset if enabled
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `activation not completed — returns false`() {
        sut.activationProgress = ActivationProgress.NOT_STARTED
        setDrift(10, 0, 0.55)   // drift = -0.05, in correction zone if activated
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `pod suspended — returns false`() {
        sut.podState.deliveryStatus = DeliveryStatus.SUSPENDED
        setDrift(10, 0, 0.55)
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `pod kaput (ALARM) — returns false`() {
        sut.podState.podStatus = PodStatus.ALARM
        setDrift(10, 0, 0.55)
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `pod kaput (DEACTIVATED) — returns false`() {
        sut.podState.podStatus = PodStatus.DEACTIVATED
        setDrift(10, 0, 0.55)
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `within cooldown window — returns false`() {
        sut.lastBasalCorrectionTime = System.currentTimeMillis() - 30_000L  // 30 s ago, < 2 min
        setDrift(10, 0, 0.55)
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    // ---- initialisation state ---------------------------------------------------------------

    @Test fun `basalExpected null (first status after activation) — drift is zero, returns false`() {
        // When basalExpected is null the basalDrift getter falls back to basalDelivered,
        // making drift = 0. No correction should trigger in this initialisation state.
        sut.podState.pulsesDelivered = 10
        sut.podState.bolusPulsesDelivered = 0
        sut.podState.basalExpected = null
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    // ---- drift magnitude checks ---------------------------------------------------------------

    @Test fun `bolus pulses non-zero — only basal portion drives drift`() {
        // 20 total pulses, 10 bolus pulses → 10 basal pulses delivered = 0.50 U.
        // Expected 0.55 U → drift = -0.05, in correction zone.
        setDrift(totalPulses = 20, bolusPulses = 10, expectedUnits = 0.55)
        assertThat(sut.needsBasalCorrection()).isTrue()
    }

    @Test fun `non-zero TBR active, drift in zone — returns true`() {
        // A running non-zero TBR should not suppress correction (only zero-rate TBR does).
        sut.tempBasal = OmnipodDashPodStateManager.TempBasal(
            startTime         = System.currentTimeMillis() - 10 * 60_000L,
            durationInMinutes = 30,
            rate              = 1.0
        )
        setDrift(10, 0, 0.55)   // drift = -0.05
        assertThat(sut.needsBasalCorrection()).isTrue()
    }

    @Test fun `zero drift — returns false`() {
        setDrift(10, 0, 0.50)   // delivered = 0.50, expected = 0.50 → drift = 0
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `small over-delivery (positive drift) — returns false`() {
        setDrift(10, 0, 0.45)   // drift = +0.05; over-delivery not compensated
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `drift slightly above correction threshold — returns false`() {
        setDrift(10, 0, 0.52)   // drift = -0.02, above -0.025 threshold
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `drift slightly below correction threshold — returns true`() {
        setDrift(10, 0, 0.53)   // drift = -0.03, below -0.025 threshold
        assertThat(sut.needsBasalCorrection()).isTrue()
    }

    @Test fun `drift in correction zone (one full pulse) — returns true`() {
        setDrift(10, 0, 0.55)   // drift = -0.05 (1 pulse under-delivery)
        assertThat(sut.needsBasalCorrection()).isTrue()
    }

    @Test fun `drift exceeds lower boundary — resets expected and returns false`() {
        // 3 pulses = 0.15U under-delivery: clearly beyond the 2-pulse (0.10U) reset boundary.
        // Using a margin avoids IEEE 754 round-trip errors that arise at the exact boundary.
        setDrift(10, 0, 0.65)   // drift = 0.5 - 0.65 = -0.15
        assertThat(sut.needsBasalCorrection()).isFalse()
        assertThat(sut.podState.basalExpected).isEqualTo(0.5)  // reset to actual
    }

    @Test fun `drift exceeds upper boundary — resets expected and returns false`() {
        // 3 pulses = 0.15U over-delivery: clearly beyond the 2-pulse (0.10U) reset boundary.
        setDrift(10, 0, 0.35)   // drift = 0.5 - 0.35 = +0.15
        assertThat(sut.needsBasalCorrection()).isFalse()
        assertThat(sut.podState.basalExpected).isEqualTo(0.5)  // reset to actual
    }

    // ---- zero-TBR safety check ----------------------------------------------------------------

    @Test fun `drift in zone, zero TBR, no recent bolus — returns false`() {
        sut.tempBasal = OmnipodDashPodStateManager.TempBasal(
            startTime         = System.currentTimeMillis() - 10 * 60_000L,
            durationInMinutes = 30,
            rate              = 0.0
        )
        setDrift(10, 0, 0.55)   // drift = -0.05
        assertThat(sut.needsBasalCorrection()).isFalse()
    }

    @Test fun `drift in zone, zero TBR, but recent bolus — returns true`() {
        sut.tempBasal = OmnipodDashPodStateManager.TempBasal(
            startTime         = System.currentTimeMillis() - 10 * 60_000L,
            durationInMinutes = 30,
            rate              = 0.0
        )
        // createLastBolus sets startTime = now, satisfying the < 5 min recency check
        sut.createLastBolus(requestedUnits = 1.0, historyId = 1L, bolusType = BS.Type.NORMAL)
        setDrift(10, 0, 0.55)   // drift = -0.05
        assertThat(sut.needsBasalCorrection()).isTrue()
    }

    // ---- cooldown expiry ----------------------------------------------------------------------

    @Test fun `past cooldown window — does not block correction`() {
        sut.lastBasalCorrectionTime = System.currentTimeMillis() - 3 * 60_000L  // 3 min ago, > 2 min
        setDrift(10, 0, 0.55)   // drift = -0.05
        assertThat(sut.needsBasalCorrection()).isTrue()
    }
}
