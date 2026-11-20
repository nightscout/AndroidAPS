package app.aaps.core.interfaces.source

/**
 * Implemented by a [BgSource] that knows the state of its sensor and wants AAPS to raise the
 * sensor alarms for it (sensor failed, ended, ending soon, no signal, sensor issue, warm-up done,
 * second sensor ready).
 *
 * Optional: a source that does not implement it gets no sensor alarm at all, and nothing changes
 * for it. The alarms are raised in one central place (`LocalAlertUtils.checkGlucoseAlerts`), which
 * reads the active source as `activeBgSource as? CgmSensorAlertProvider`.
 *
 * The source only says what is true now. The user settings (which alarm is on, how long a signal
 * may be lost before it warns, how often it repeats) are applied by the central check. The one
 * exception is the warning time before the end, which the check passes in, see
 * [CgmSensorAlertState.Expiring].
 *
 * "Ending soon" and "ended" are measured against the end of the sensor's usable time, including
 * any grace period the source knows of (a Dexcom ONE+ / G7 has 12 hours). The
 * source shows the same end on its own status screen.
 *
 * The alarms only notify. Nothing here may change the glucose values the loop receives or dosing.
 */
interface CgmSensorAlertProvider {

    /**
     * The most important sensor state at [nowMs]. One state at a time, in this order:
     * [CgmSensorAlertState.Failed] > [CgmSensorAlertState.Expired] > [CgmSensorAlertState.SignalLost] >
     * [CgmSensorAlertState.SensorIssue] > [CgmSensorAlertState.Expiring] >
     * [CgmSensorAlertState.SecondSensorReady] > [CgmSensorAlertState.WarmupDone] > [CgmSensorAlertState.Ok].
     *
     * A stopped source answers [CgmSensorAlertState.Ok].
     *
     * A function and not a flow on purpose: several states only depend on the clock (ending soon,
     * ended, no reading for a while), so the answer is computed when it is asked for. It must be
     * cheap and safe to call from any thread. [CgmSensorAlertRules.map] holds the order, so every
     * source keeps the same one.
     *
     * @param expiringLeadMs how long before the end the user wants "ending soon". Before that the
     *   source must not answer [CgmSensorAlertState.Expiring], so that a less important state, for
     *   example a ready second sensor, is not hidden while there is nothing to warn about yet.
     */
    fun sensorAlertState(nowMs: Long, expiringLeadMs: Long): CgmSensorAlertState
}

/**
 * State of the sensor, for the sensor alarms. Times are wall-clock epoch ms.
 *
 * Each timestamp also names the episode: the same state with the same timestamp is the same
 * episode, and its alarm is raised only once for it.
 */
sealed interface CgmSensorAlertState {

    /** Nothing to report. */
    data object Ok : CgmSensorAlertState

    /** The first usable reading after a warm-up arrived at [atEpochMs]. */
    data class WarmupDone(val atEpochMs: Long) : CgmSensorAlertState

    /** The usable time of the sensor ends at [endsAtEpochMs], including any grace period. */
    data class Expiring(val endsAtEpochMs: Long) : CgmSensorAlertState

    /**
     * The usable time of the sensor ended at [atEpochMs], including any grace period.
     *
     * [atEpochMs] must stay the same for the whole episode: the end of the usable time when the
     * source knows it, also when the sensor reports its end by itself and after an app restart.
     * Never the moment a message happened to be seen, because each new value is a new episode and
     * sounds the alarm again.
     */
    data class Expired(val atEpochMs: Long) : CgmSensorAlertState

    /** The sensor cannot be used. No free text, so the user always sees a translated message. */
    data class Failed(val reason: CgmSensorFailure) : CgmSensorAlertState

    /**
     * There has been no link to the sensor, or no reading from it, since [sinceEpochMs]. The source
     * says so at once; the central check waits for the delay the user has set before it warns, and
     * it counts that delay from [sinceEpochMs].
     */
    data class SignalLost(val sinceEpochMs: Long) : CgmSensorAlertState

    /**
     * The sensor is linked and sends, but without a usable glucose value (for example too much
     * noise), since [sinceEpochMs]. It has not failed and has not ended. The central check waits for
     * the delay the user has set, counted from [sinceEpochMs].
     */
    data class SensorIssue(val sinceEpochMs: Long) : CgmSensorAlertState

    /** A pre-soaked second sensor is ready since [atEpochMs], so the user can switch to it. */
    data class SecondSensorReady(val atEpochMs: Long) : CgmSensorAlertState
}

/** Why a sensor cannot be used, see [CgmSensorAlertState.Failed]. */
enum class CgmSensorFailure {

    /** The sensor itself reports a fault. */
    SENSOR_ERROR,

    /** The sensor stopped before the end of its life. */
    ENDED_EARLY,

    /** Any other final failure, for example the sensor cannot be paired. */
    OTHER
}

/**
 * The priority order of [CgmSensorAlertProvider.sensorAlertState], in one place, so every source
 * answers the same way. A source fills [Input] from what its driver knows and calls [map].
 *
 * Pure: no clock of its own, no platform code.
 */
object CgmSensorAlertRules {

    /**
     * No reading for this long, while a reading is expected, is a lost signal. This only makes the
     * state visible: the central check waits for the delay the user has set before it warns.
     */
    const val SILENCE_MS: Long = 5L * 60L * 1000L

    /**
     * Everything the order needs. Times are wall-clock epoch ms.
     *
     * @param stopped the source is stopped: no state at all.
     * @param sensorStored a sensor is set up in the main slot. Without one only a ready second
     *   sensor is worth a message.
     * @param failure the sensor cannot be used, null when it can.
     * @param endedBySensor the sensor itself says its session has ended. It is [CgmSensorAlertState.Expired]
     *   when the usable time is over, else [CgmSensorFailure.ENDED_EARLY].
     * @param expiresAtMs end of the usable time including any grace period, null when not known.
     * @param linkLostSinceMs the link is down since then, null while it is up.
     * @param lastReadingAtMs the last usable reading, null when none yet.
     * @param readingExpected false while no reading comes on purpose (warm-up, pairing), so silence
     *   is not a lost signal then.
     * @param issueSinceMs the sensor sends without a usable value since then, null when it does not.
     * @param secondSensorReadyAtMs a pre-soak sensor is ready since then, null when it is not.
     * @param warmupDoneAtMs the first usable reading after a warm-up, null when none or too old.
     * @param expiringLeadMs see [CgmSensorAlertProvider.sensorAlertState].
     */
    data class Input(
        val stopped: Boolean,
        val sensorStored: Boolean,
        val failure: CgmSensorFailure?,
        val endedBySensor: Boolean,
        val expiresAtMs: Long?,
        val linkLostSinceMs: Long?,
        val lastReadingAtMs: Long?,
        val readingExpected: Boolean,
        val issueSinceMs: Long?,
        val secondSensorReadyAtMs: Long?,
        val warmupDoneAtMs: Long?,
        val expiringLeadMs: Long,
        val nowMs: Long,
    )

    fun map(input: Input): CgmSensorAlertState {
        if (input.stopped) return CgmSensorAlertState.Ok
        if (!input.sensorStored) {
            return input.secondSensorReadyAtMs?.let { CgmSensorAlertState.SecondSensorReady(it) } ?: CgmSensorAlertState.Ok
        }
        val now = input.nowMs
        val expiresAt = input.expiresAtMs
        // The end, once it has come. Null before it, or when the end is not known.
        val endedAt = expiresAt?.takeIf { now >= it }
        input.failure?.let { return CgmSensorAlertState.Failed(it) }
        if (input.endedBySensor) {
            return if (endedAt != null) CgmSensorAlertState.Expired(endedAt)
            else CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY)
        }
        if (endedAt != null) return CgmSensorAlertState.Expired(endedAt)
        input.linkLostSinceMs?.let { return CgmSensorAlertState.SignalLost(it) }
        val last = input.lastReadingAtMs
        if (last != null && input.readingExpected && now - last >= SILENCE_MS) return CgmSensorAlertState.SignalLost(last)
        input.issueSinceMs?.let { return CgmSensorAlertState.SensorIssue(it) }
        if (expiresAt != null && expiresAt - now <= input.expiringLeadMs) return CgmSensorAlertState.Expiring(expiresAt)
        input.secondSensorReadyAtMs?.let { return CgmSensorAlertState.SecondSensorReady(it) }
        input.warmupDoneAtMs?.let { return CgmSensorAlertState.WarmupDone(it) }
        return CgmSensorAlertState.Ok
    }
}
