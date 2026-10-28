package app.aaps.plugins.source.compose

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState

// Maps the Dexcom ONE+ driver warm-up state onto the vendor-neutral CgmWarmupInfo. The mapping is
// one to one on every phase, so the screens and the notifications see exactly what the driver said.

/** Dexcom ONE+ driver phase as the vendor-neutral phase. */
fun OnePlusWarmupState.Phase.toCgmWarmupPhase(): CgmWarmupPhase = when (this) {
    OnePlusWarmupState.Phase.IDLE         -> CgmWarmupPhase.IDLE
    OnePlusWarmupState.Phase.PAIRING      -> CgmWarmupPhase.PAIRING
    OnePlusWarmupState.Phase.CONNECTING   -> CgmWarmupPhase.CONNECTING
    OnePlusWarmupState.Phase.RECONNECTING -> CgmWarmupPhase.RECONNECTING
    OnePlusWarmupState.Phase.WARMING      -> CgmWarmupPhase.WARMING
    OnePlusWarmupState.Phase.READY        -> CgmWarmupPhase.READY
    OnePlusWarmupState.Phase.FAILED       -> CgmWarmupPhase.FAILED
}

/** Dexcom ONE+ driver state as the three facts the countdown rules read. */
fun OnePlusWarmupState.toCgmWarmupInfo(): CgmWarmupInfo = CgmWarmupInfo(
    phase = phase.toCgmWarmupPhase(),
    remainingMs = remainingMs,
    endsAtEpochMs = endsAtEpochMs,
)

/** Short form for the screens: a Dexcom ONE+ phase straight to what it means for the user. */
fun OnePlusWarmupState.Phase.toUiState(): CgmUiState = toCgmWarmupPhase().toUiState()
