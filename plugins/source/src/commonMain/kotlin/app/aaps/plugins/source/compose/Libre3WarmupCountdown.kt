package app.aaps.plugins.source.compose

/**
 * Works out what the countdown should show.
 *
 * Pure, so the rule is unit tested rather than eyeballed on a screen. The driver state is mapped
 * onto [CgmWarmupInfo] on Android, so this rule stays shared code.
 */
object Libre3WarmupCountdown {

    /**
     * @param nowMs the phone clock.
     * @return milliseconds left, or null when nothing is known and the screen should show a dash.
     */
    fun remainingMs(state: CgmWarmupInfo, nowMs: Long): Long? {
        // What the sensor said beats anything worked out here.
        state.remainingMs?.let { return maxOf(0L, it) }
        state.endsAtEpochMs?.let { return maxOf(0L, it - nowMs) }
        return null
    }

    /** Minutes and seconds, as `mm:ss`. */
    fun format(remainingMs: Long): String {
        val totalSeconds = remainingMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    /** True once there is nothing left to wait for. */
    fun isFinished(state: CgmWarmupInfo, nowMs: Long): Boolean {
        if (state.phase == CgmWarmupPhase.READY) return true
        val remaining = remainingMs(state, nowMs) ?: return false
        return remaining <= 0L
    }
}
