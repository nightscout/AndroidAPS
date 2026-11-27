package app.aaps.plugins.source.compose

/**
 * Warm-up countdown resolution for the Dexcom ONE+ UI.
 *
 * Priority:
 * 1. Protocol [CgmWarmupInfo.remainingMs] when non-null (preferred).
 * 2. Else [CgmWarmupInfo.endsAtEpochMs] → remaining = endsAt − now.
 * 3. Else a **local fallback** timer (`localFallbackEndsAtEpochMs`) — used ONLY when the
 *    protocol does not expose remaining/end. Do not invent a second clock when remainingMs
 *    is already provided by the driver.
 *
 * The driver state is mapped onto [CgmWarmupInfo] first, so this rule does not need the driver.
 */
object DexcomOnePlusWarmupCountdown {

    /** Nominal warm-up length used only for the local fallback path (~30 min). */
    const val LOCAL_FALLBACK_DURATION_MS: Long = 30L * 60L * 1000L

    /**
     * @param state current driver warm-up state
     * @param nowEpochMs wall clock now
     * @param localFallbackEndsAtEpochMs end time for the local-only timer; null if not started
     * @return remaining ms to show, or null when no countdown applies
     */
    fun resolveRemainingMs(
        state: CgmWarmupInfo,
        nowEpochMs: Long,
        localFallbackEndsAtEpochMs: Long?,
    ): Long? {
        state.remainingMs?.let { return it.coerceAtLeast(0L) }
        state.endsAtEpochMs?.let { return (it - nowEpochMs).coerceAtLeast(0L) }
        // Local fallback ONLY when protocol remaining/end are both null.
        localFallbackEndsAtEpochMs?.let { return (it - nowEpochMs).coerceAtLeast(0L) }
        return null
    }

    fun shouldStartLocalFallback(state: CgmWarmupInfo): Boolean =
        state.phase == CgmWarmupPhase.WARMING &&
            state.remainingMs == null &&
            state.endsAtEpochMs == null

    /**
     * The local fallback deadline belongs to the whole warm-up, not to a single phase: a duty-cycle
     * disconnect moves the phase to CONNECTING / RECONNECTING and must keep the clock. Dropping it
     * there blanked the countdown and restarted a fresh ~30 min timer on the next WARMING packet.
     * Only a finished / stopped / failed warm-up clears it.
     */
    fun shouldClearLocalFallback(phase: CgmWarmupPhase): Boolean = when (phase) {
        CgmWarmupPhase.READY,
        CgmWarmupPhase.IDLE,
        CgmWarmupPhase.FAILED       -> true

        CgmWarmupPhase.WARMING,
        CgmWarmupPhase.PAIRING,
        CgmWarmupPhase.CONNECTING,
        CgmWarmupPhase.RECONNECTING -> false
    }

    /** Phases that show the mm:ss countdown when one is known (the link may be re-establishing). */
    fun showsCountdown(phase: CgmWarmupPhase): Boolean = when (phase) {
        CgmWarmupPhase.WARMING,
        CgmWarmupPhase.IDLE,
        CgmWarmupPhase.PAIRING,
        CgmWarmupPhase.CONNECTING,
        CgmWarmupPhase.RECONNECTING -> true

        CgmWarmupPhase.READY,
        CgmWarmupPhase.FAILED       -> false
    }

    fun formatMmSs(remainingMs: Long): String {
        val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSec / 60L
        val seconds = totalSec % 60L
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
