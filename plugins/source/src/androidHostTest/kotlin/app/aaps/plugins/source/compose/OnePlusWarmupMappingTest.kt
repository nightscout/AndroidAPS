package app.aaps.plugins.source.compose

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The ONE+ warm-up mapping: every driver phase lands on the same vendor-neutral phase, so the
 * screens and the notification read the driver the same way.
 */
class OnePlusWarmupMappingTest {

    @Test
    fun everyDriverPhaseMapsToTheSharedPhaseOfTheSameName() {
        OnePlusWarmupState.Phase.entries.forEach { phase ->
            assertEquals(phase.name, phase.toCgmWarmupPhase().name)
        }
    }

    @Test
    fun theCountdownFactsAreCarriedOverUnchanged() {
        val info = OnePlusWarmupState(
            phase = OnePlusWarmupState.Phase.WARMING,
            remainingMs = 120_000L,
            endsAtEpochMs = 5_000_000L,
        ).toCgmWarmupInfo()

        assertEquals(CgmWarmupInfo(CgmWarmupPhase.WARMING, 120_000L, 5_000_000L), info)
    }
}
