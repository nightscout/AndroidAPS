package app.aaps.plugins.source.compose

import app.aaps.plugins.libre3.Libre3WarmupState

// Maps the Libre 3 driver warm-up state onto the shared CgmWarmupInfo. The Libre 3 driver is
// Android only, so it can only be named here. The mapping is one to one on every phase.

/** Libre 3 driver phase as the shared phase. */
fun Libre3WarmupState.Phase.toCgmWarmupPhase(): CgmWarmupPhase = when (this) {
    Libre3WarmupState.Phase.IDLE         -> CgmWarmupPhase.IDLE
    Libre3WarmupState.Phase.PAIRING      -> CgmWarmupPhase.PAIRING
    Libre3WarmupState.Phase.CONNECTING   -> CgmWarmupPhase.CONNECTING
    Libre3WarmupState.Phase.RECONNECTING -> CgmWarmupPhase.RECONNECTING
    Libre3WarmupState.Phase.WARMING      -> CgmWarmupPhase.WARMING
    Libre3WarmupState.Phase.READY        -> CgmWarmupPhase.READY
    Libre3WarmupState.Phase.FAILED       -> CgmWarmupPhase.FAILED
}

/** Libre 3 driver state as the three facts the countdown rules read. */
fun Libre3WarmupState.toCgmWarmupInfo(): CgmWarmupInfo = CgmWarmupInfo(
    phase = phase.toCgmWarmupPhase(),
    remainingMs = remainingMs,
    endsAtEpochMs = endsAtEpochMs,
)

/** Short form for the screens: a Libre 3 phase straight to what it means for the user. */
fun Libre3WarmupState.Phase.toUiState(): CgmUiState = toCgmWarmupPhase().toUiState()
