package app.aaps.core.interfaces.source

import kotlinx.coroutines.flow.StateFlow

/**
 * Generic warm-up / reconnect status of an active CGM source.
 *
 * A [BgSource] that can report this implements [CgmWarmupProvider]. UI and safety can then
 * react to a sensor that is warming up or reconnecting (no glucose yet) without naming a vendor.
 *
 * @param active true while warm-up or reconnect is in progress.
 * @param phase honest phase of the current wait.
 * @param remainingMs countdown in ms if the protocol knows it, else null.
 * @param endsAtEpochMs wall-clock end in epoch ms if known, else null.
 * @param totalMs full warm-up length in ms if known; lets UI draw a determinate ring.
 */
data class CgmWarmupStatus(
    val active: Boolean,
    val phase: Phase,
    val remainingMs: Long?,
    val endsAtEpochMs: Long?,
    val totalMs: Long? = null,
) {

    enum class Phase { CONNECTING, RECONNECTING, WARMING, PAIRING }
}

/**
 * Implemented by a [BgSource] plugin that can report a generic [CgmWarmupStatus].
 *
 * Consumers cast the active BG source: `(activeBgSource as? CgmWarmupProvider)?.warmupStatus`.
 */
interface CgmWarmupProvider {

    /** Current warm-up status, or null when there is nothing to show (READY / IDLE / FAILED). */
    val warmupStatus: StateFlow<CgmWarmupStatus?>
}
