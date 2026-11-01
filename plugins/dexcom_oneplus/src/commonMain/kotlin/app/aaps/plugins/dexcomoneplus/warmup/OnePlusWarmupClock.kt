package app.aaps.plugins.dexcomoneplus.warmup

import app.aaps.plugins.dexcomoneplus.OnePlusWarmupState

/**
 * Pure warm-up helpers for protocol / UI (~30 min factory warm-up, Q12).
 * Prefer protocol-reported [OnePlusWarmupState.remainingMs] when the stack provides it.
 */
object OnePlusWarmupClock {

    /** Factory default warm-up duration used when estimating locally. */
    const val DEFAULT_WARMUP_MS: Long = 30L * 60L * 1000L

    /**
     * Remaining ms from explicit fields; does not invent a local 30 min clock.
     * Order: [remainingMs] → [endsAtEpochMs] − now → null.
     */
    fun resolveRemainingMs(state: OnePlusWarmupState, nowMs: Long): Long? {
        state.remainingMs?.let { return it.coerceAtLeast(0L) }
        val endsAt = state.endsAtEpochMs ?: return null
        return (endsAt - nowMs).coerceAtLeast(0L)
    }

    /**
     * Builds a WARMING state from session start + duration (estimate until protocol clock exists).
     */
    fun warmingFromStart(
        startEpochMs: Long,
        durationMs: Long = DEFAULT_WARMUP_MS,
        nowMs: Long,
        message: String? = null,
    ): OnePlusWarmupState {
        val endsAt = startEpochMs + durationMs
        val remaining = (endsAt - nowMs).coerceAtLeast(0L)
        val phase = if (remaining <= 0L) OnePlusWarmupState.Phase.READY else OnePlusWarmupState.Phase.WARMING
        return OnePlusWarmupState(
            phase = phase,
            remainingMs = if (phase == OnePlusWarmupState.Phase.READY) 0L else remaining,
            endsAtEpochMs = endsAt,
            message = message,
        )
    }
}
