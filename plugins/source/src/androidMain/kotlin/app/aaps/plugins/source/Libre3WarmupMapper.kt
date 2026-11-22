package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmWarmupStatus
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.plugins.libre3.warmup.Libre3WarmupClock

/**
 * Turns the driver's own state into the generic warm-up view that the plugin publishes as a
 * `CgmWarmupProvider`. The Libre 3 screens and the warm-up notification read it.
 *
 * Pure, so the rule "when does the ring show, and how full is it" is unit tested on its own.
 */
object Libre3WarmupMapper {

    /** The whole warm-up length, used to draw how far along the ring is. */
    val DEFAULT_TOTAL_MS = Libre3WarmupClock.DEFAULT_WARMUP_MINUTES * 60_000L

    /**
     * @return what should be shown, or null when there is nothing to show, that is when
     *   the sensor is running normally, is doing nothing, or has finally failed.
     */
    fun toCgmWarmupStatus(state: Libre3WarmupState, totalMs: Long = DEFAULT_TOTAL_MS): CgmWarmupStatus? {
        val phase = when (state.phase) {
            Libre3WarmupState.Phase.WARMING      -> CgmWarmupStatus.Phase.WARMING
            Libre3WarmupState.Phase.PAIRING      -> CgmWarmupStatus.Phase.PAIRING
            Libre3WarmupState.Phase.CONNECTING   -> CgmWarmupStatus.Phase.CONNECTING
            Libre3WarmupState.Phase.RECONNECTING -> CgmWarmupStatus.Phase.RECONNECTING
            // Running, doing nothing, or finally failed: the ring has nothing to say.
            Libre3WarmupState.Phase.READY,
            Libre3WarmupState.Phase.IDLE,
            Libre3WarmupState.Phase.FAILED       -> return null
        }
        return CgmWarmupStatus(
            active = true,
            phase = phase,
            remainingMs = state.remainingMs,
            endsAtEpochMs = state.endsAtEpochMs,
            // Only a real warm-up has a known whole length. While connecting, the ring must not
            // pretend to know how long it will take.
            totalMs = if (phase == CgmWarmupStatus.Phase.WARMING) totalMs else null,
        )
    }
}
