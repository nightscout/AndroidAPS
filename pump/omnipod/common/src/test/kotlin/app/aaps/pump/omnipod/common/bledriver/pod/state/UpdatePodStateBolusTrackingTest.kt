package app.aaps.pump.omnipod.common.bledriver.pod.state

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.AlertType
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodStatus
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.util.EnumSet
import java.util.TimeZone

/**
 * Tests for the bolus-pulse attribution logic inside [OmnipodDashPodStateManagerImpl.updatePodState].
 *
 * The core invariant: new pulses are attributed to the bolus counter only when
 *   - an incomplete bolus is in progress ([PodState.lastBolus.deliveryComplete] == false), AND
 *   - a basal correction is NOT currently delivering ([PodState.basalCorrectionInProgress] == false).
 *
 * When a basal correction is in progress its pulses must NOT be counted as bolus pulses,
 * otherwise [basalDelivered] (= total − bolus) would decrease and mask the correction
 * against [basalExpected], corrupting the drift calculation.
 */
class UpdatePodStateBolusTrackingTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var config: Config

    private lateinit var sut: OmnipodDashPodStateManagerImpl

    // ---- setup --------------------------------------------------------------------------------

    @BeforeEach fun setUp() {
        sut = OmnipodDashPodStateManagerImpl(aapsLogger, rxBus, preferences, config)
        sut.activationProgress = ActivationProgress.COMPLETED

        // Baseline: 100 total pulses, 60 attributed as bolus → 40 basal pulses
        sut.podState.pulsesDelivered        = 100
        sut.podState.bolusPulsesDelivered   = 60
        sut.podState.basalExpected          = 40 * 0.05   // 2.0 U
        sut.podState.lastUpdatedSystem      = System.currentTimeMillis() - 1000
    }

    // ---- helpers ------------------------------------------------------------------------------

    /**
     * Calls [updatePodState] with a minimal set of parameters.
     * [totalPulsesDelivered] and [bolusPulsesRemaining] are the only values that vary per test.
     */
    private fun statusUpdate(totalPulsesDelivered: Int, bolusPulsesRemaining: Int = 0) {
        sut.updatePodState(
            deliveryStatus                      = DeliveryStatus.BASAL_ACTIVE,
            podStatus                           = PodStatus.RUNNING_ABOVE_MIN_VOLUME,
            totalPulsesDelivered                = totalPulsesDelivered.toShort(),
            reservoirPulsesRemaining            = 1000,
            sequenceNumberOfLastProgrammingCommand = 0,
            minutesSinceActivation              = 60,
            activeAlerts                        = EnumSet.noneOf(AlertType::class.java),
            bolusPulsesRemaining                = bolusPulsesRemaining.toShort()
        )
    }

    // ---- no bolus in progress -----------------------------------------------------------------

    @Test fun `no active bolus — new pulses not counted as bolus`() {
        // lastBolus is null (no bolus has ever run); bolus counter must stay unchanged
        statusUpdate(totalPulsesDelivered = 103)
        assertThat(sut.podState.bolusPulsesDelivered).isEqualTo(60)
    }

    @Test fun `completed bolus — new pulses not counted as bolus`() {
        sut.createLastBolus(requestedUnits = 1.0, historyId = 1L, bolusType = BS.Type.NORMAL)
        sut.markLastBolusComplete()
        statusUpdate(totalPulsesDelivered = 103)
        assertThat(sut.podState.bolusPulsesDelivered).isEqualTo(60)
    }

    // ---- active bolus in progress -------------------------------------------------------------

    @Test fun `active bolus, no concurrent basal — pulses attributed to bolus`() {
        sut.createLastBolus(requestedUnits = 0.15, historyId = 1L, bolusType = BS.Type.NORMAL)
        // 3 new total pulses, 3 bolus pulses remaining → 0 now: clean bolus delivery
        statusUpdate(totalPulsesDelivered = 103, bolusPulsesRemaining = 0)
        assertThat(sut.podState.bolusPulsesDelivered).isEqualTo(63)
    }

    @Test fun `active bolus with concurrent basal pulse — only bolus portion attributed`() {
        // 5 total new pulses: 3 from bolus (remaining 3→0) + 2 from basal running in parallel.
        // bolusPulsesDelivered must increase by 3 only; the 2 basal pulses must NOT be counted.
        sut.createLastBolus(requestedUnits = 0.15, historyId = 1L, bolusType = BS.Type.NORMAL)
        // Set bolus remaining so previousBolusPulsesRemaining = 3
        sut.podState.lastBolus!!.bolusUnitsRemaining = 3 * 0.05
        statusUpdate(totalPulsesDelivered = 105, bolusPulsesRemaining = 0)
        assertThat(sut.podState.bolusPulsesDelivered).isEqualTo(63)  // 60 + 3, not 60 + 5
    }

    // ---- basal correction in progress ---------------------------------------------------------

    @Test fun `basal correction in progress — pulse increase NOT attributed to bolus`() {
        // Simulate the correction scenario: an incomplete bolus exists (the correction
        // bolus itself), but basalCorrectionInProgress prevents those pulses from
        // being counted as bolus pulses. This keeps basalDelivered correct.
        sut.createLastBolus(requestedUnits = 0.05, historyId = 1L, bolusType = BS.Type.NORMAL)
        sut.basalCorrectionInProgress = true

        statusUpdate(totalPulsesDelivered = 102, bolusPulsesRemaining = 0)

        // bolus counter must be unchanged: the correction pulse is basal, not bolus
        assertThat(sut.podState.bolusPulsesDelivered).isEqualTo(60)
        // total went up by 2, bolus unchanged → basal pulses = 42 → basalDelivered = 2.10 U
        assertThat(sut.podState.pulsesDelivered).isEqualTo(102)
    }

    @Test fun `basal correction flag cleared after correction — subsequent bolus pulses tracked again`() {
        sut.createLastBolus(requestedUnits = 0.05, historyId = 1L, bolusType = BS.Type.NORMAL)
        sut.basalCorrectionInProgress = true
        statusUpdate(totalPulsesDelivered = 101, bolusPulsesRemaining = 0)
        // correction delivered; flag cleared
        sut.basalCorrectionInProgress = false

        // Now a new bolus starts
        sut.createLastBolus(requestedUnits = 0.10, historyId = 2L, bolusType = BS.Type.NORMAL)
        statusUpdate(totalPulsesDelivered = 103, bolusPulsesRemaining = 0)

        // 2 new pulses from the second bolus should be attributed
        assertThat(sut.podState.bolusPulsesDelivered).isEqualTo(62)
    }

    // ---- counter seeding and accumulation ----------------------------------------------------

    @Test fun `bolusPulsesDelivered null — seeded from totalPulsesDelivered on first update`() {
        // On the very first status response after activation, bolusPulsesDelivered is null.
        // The ?: branch seeds it with totalPulsesDelivered so future delta tracking starts
        // from the correct baseline (i.e. no bolus pulses assumed yet).
        sut.podState.bolusPulsesDelivered = null

        statusUpdate(totalPulsesDelivered = 42)

        assertThat(sut.podState.bolusPulsesDelivered).isEqualTo(42)
        // basalExpected is seeded to 0.0 U: because bolusPulsesDelivered was null at seeding time,
        // basalPulsesDelivered can't be computed and basalDelivered falls back to 0.0 U. Both
        // counters therefore start from zero together, so initial drift = 0.
        assertThat(sut.podState.basalExpected).isEqualTo(0.0)
    }

    @Test fun `basalExpected null — seeded from basalDelivered on first update`() {
        // On the very first status response, basalExpected is null.
        // The ?: branch seeds it with the current basalDelivered so drift starts at zero.
        // setUp: pulsesDelivered=100 bolusPulsesDelivered=60 → basalDelivered=(100−60)×0.05=2.0 U
        sut.podState.basalExpected = null

        statusUpdate(totalPulsesDelivered = 100)

        // Seeded to 2.0 U = (100 − 60 pulses) × 0.05 U/pulse → initial drift = 0.
        assertThat(sut.podState.basalExpected).isEqualTo(2.0)
    }

    @Test fun `basalExpected accumulates delta from integrateExpectedDelivery`() {
        // Verify that the basalExpected += delta wiring works end-to-end:
        // 1 hour at 1.0 U/h → delta ≈ 1.0 U → basalExpected goes from 2.0 to ≈ 3.0.
        val savedTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            sut.podState.timeZoneOffset    = 0
            sut.podState.basalProgram      = BasalProgram(listOf(BasalProgram.Segment(0, 48, 100)))
            sut.podState.lastUpdatedSystem = System.currentTimeMillis() - 3_600_000L
            sut.podState.basalExpected     = 2.0

            statusUpdate(totalPulsesDelivered = 100)

            // Allow ±10 ms worth of basal (≈ 0.0003 U) for scheduling jitter
            assertThat(sut.podState.basalExpected!!).isWithin(0.001).of(3.0)
        } finally {
            TimeZone.setDefault(savedTz)
        }
    }
}
