package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState
import app.aaps.plugins.source.compose.DexcomOnePlusWarmupCountdown

/**
 * Pure mapping of the native [OnePlusWarmupState] onto the generic [CgmWarmupStatus] consumed by the
 * warm-up ring and the ongoing notification.
 *
 * Active phases (WARMING / CONNECTING / RECONNECTING / PAIRING) → an active status carrying the
 * countdown/message; terminal or idle phases (READY / IDLE / FAILED) → `null` (nothing to show).
 *
 * Extracted from [DexcomOnePlusPlugin] so it is unit-testable without the DI graph.
 */
internal object DexcomOnePlusWarmupMapper {

    fun toCgmWarmupStatus(state: OnePlusWarmupState): CgmWarmupStatus? {
        val mappedPhase = when (state.phase) {
            OnePlusWarmupState.Phase.WARMING      -> CgmWarmupStatus.Phase.WARMING
            OnePlusWarmupState.Phase.CONNECTING   -> CgmWarmupStatus.Phase.CONNECTING
            OnePlusWarmupState.Phase.RECONNECTING -> CgmWarmupStatus.Phase.RECONNECTING
            OnePlusWarmupState.Phase.PAIRING      -> CgmWarmupStatus.Phase.PAIRING
            OnePlusWarmupState.Phase.READY,
            OnePlusWarmupState.Phase.IDLE,
            OnePlusWarmupState.Phase.FAILED       -> return null
        }
        return CgmWarmupStatus(
            active = true,
            phase = mappedPhase,
            remainingMs = state.remainingMs,
            endsAtEpochMs = state.endsAtEpochMs,
            // Nominal ONE+/G7 warm-up length (~30 min) so the warm-up ring fills. Supplied while
            // warming, and also when a connection phase still carries the sticky warm-up deadline
            // (a duty-cycle reconnect during warm-up must not blank the ring). Phases without any
            // deadline have no countdown total → indeterminate ring.
            totalMs = if (mappedPhase == CgmWarmupStatus.Phase.WARMING || state.endsAtEpochMs != null) {
                DexcomOnePlusWarmupCountdown.LOCAL_FALLBACK_DURATION_MS
            } else {
                null
            },
        )
    }
}
