package app.aaps.cgm.dexcomg7.alarm

import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.protocol.AlgorithmState
import app.aaps.cgm.dexcomg7.protocol.G7LifecycleState
import app.aaps.cgm.dexcomg7.protocol.G7Trend
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** The settings of one alarm, read from preferences. */
data class G7AlarmTypeConfig(
    val enabled: Boolean,
    val levelMgdl: Double? = null,
    /** mg/dL per minute, for rise and fall rate. */
    val rate: Int? = null,
    val delayMinutes: Int = 0,
    val repeatMinutes: Int,
    val vibrateFirst: Boolean = false,
    val sound: G7AlarmSound
)

data class G7AlarmConfig(
    val enabled: Boolean,
    val types: Map<G7AlarmType, G7AlarmTypeConfig>
) {

    operator fun get(type: G7AlarmType): G7AlarmTypeConfig = types.getValue(type)
}

/** Where one alarm stands. Times are milliseconds since the epoch. */
@Serializable
data class G7AlarmTrack(
    /** Since when the condition has held, before any delay. */
    val conditionSince: Long? = null,
    /** Since when the condition has not held. Only kept while [quietUntilCleared]. */
    val clearSince: Long? = null,
    /** The alarm is going off and nobody has acknowledged it yet. */
    val raisedAt: Long? = null,
    val lastAlertAt: Long? = null,
    /** Acknowledged: quiet until then, even if the condition lasts. */
    val snoozedUntil: Long? = null,
    /** Acknowledged with repeat set to never: quiet until the condition has gone away. */
    val quietUntilCleared: Boolean = false
) {

    val isRaised: Boolean get() = raisedAt != null
}

@Serializable
data class G7AlarmRuntime(val tracks: Map<G7AlarmType, G7AlarmTrack> = emptyMap()) {

    operator fun get(type: G7AlarmType): G7AlarmTrack = tracks[type] ?: G7AlarmTrack()

    /** Alarms going off now, most important first. */
    val raised: List<G7AlarmType> get() = G7AlarmType.entries.filter { this[it].isRaised }
}

sealed class G7AlarmAction {

    abstract val type: G7AlarmType

    /** Go off: notification and vibration, and the sound unless [withSound] is false. */
    data class Raise(override val type: G7AlarmType, val withSound: Boolean, val reminder: Boolean) : G7AlarmAction()

    /** Stop: the condition has gone, or the alarm was acknowledged. */
    data class Clear(override val type: G7AlarmType) : G7AlarmAction()
}

data class G7AlarmStep(
    val runtime: G7AlarmRuntime,
    val actions: List<G7AlarmAction>,
    /** When to look again even if no new reading arrives. Null when nothing is pending. */
    val nextCheckAt: Long?
)

/**
 * Decides when the alarms go off, the way the Dexcom G7 app does:
 *
 * - An alarm goes off when its condition holds (for its delay, if it has one).
 * - With "vibrate first" on, the first alert only vibrates.
 * - Not acknowledged: it goes off again every 5 minutes, with sound, until acknowledged or the
 *   condition goes away.
 * - Acknowledged: quiet for the alarm's repeat time. If the condition still holds after that, it
 *   goes off again as a new alert. Repeat "never" keeps it quiet until the condition has gone away.
 * - Only the most important alarm of a group goes off at a time.
 *
 * Pure: no Android, no clock of its own, so it can be tested.
 */
object G7AlarmEngine {

    /** A reading older than this no longer counts. Allows for one missed reading. */
    const val FRESH_MS = 11 * 60_000L

    /** Unacknowledged alarms go off again this often. */
    const val REMINDER_MS = 5 * 60_000L

    /** How long a condition must be gone before a "repeat never" alarm may go off again. */
    const val CLEAR_MS = 15 * 60_000L

    fun step(state: G7State, config: G7AlarmConfig, runtime: G7AlarmRuntime, now: Long): G7AlarmStep {
        if (!config.enabled) return clearAll(runtime)

        val actions = mutableListOf<G7AlarmAction>()
        val tracks = mutableMapOf<G7AlarmType, G7AlarmTrack>()
        val next = mutableListOf<Long>()
        val activeGroups = mutableSetOf<G7AlarmGroup>()

        for (type in G7AlarmType.entries) {
            val cfg = config[type]
            var track = runtime[type]
            val raw = cfg.enabled && condition(type, state, cfg, now)

            track = if (raw) track.copy(conditionSince = track.conditionSince ?: now, clearSince = null)
            else track.copy(conditionSince = null, clearSince = if (track.quietUntilCleared) track.clearSince ?: now else null)

            if (!raw && track.quietUntilCleared) {
                val clearedAt = track.clearSince!! + CLEAR_MS
                if (now >= clearedAt) track = track.copy(quietUntilCleared = false) else next += clearedAt
            }
            if (track.snoozedUntil != null && track.snoozedUntil <= now) track = track.copy(snoozedUntil = null)

            val delayEndsAt = track.conditionSince?.plus(delayMs(type, cfg))
            val active = raw && delayEndsAt!! <= now
            if (raw && !active) next += delayEndsAt!!
            if (!raw) signalLossDueAt(type, state, cfg)?.takeIf { it > now }?.let { next += it }

            val suppressed = active && type.group in activeGroups
            if (active) activeGroups += type.group

            when {
                !active || suppressed                     -> if (track.isRaised) {
                    actions += G7AlarmAction.Clear(type)
                    track = track.copy(raisedAt = null)
                }

                track.snoozedUntil != null                -> next += track.snoozedUntil
                track.quietUntilCleared                   -> Unit

                !track.isRaised                           -> {
                    actions += G7AlarmAction.Raise(type, withSound = !cfg.vibrateFirst, reminder = false)
                    track = track.copy(raisedAt = now, lastAlertAt = now)
                    next += now + REMINDER_MS
                }

                now - track.lastAlertAt!! >= REMINDER_MS -> {
                    actions += G7AlarmAction.Raise(type, withSound = true, reminder = true)
                    track = track.copy(lastAlertAt = now)
                    next += now + REMINDER_MS
                }

                else                                      -> next += track.lastAlertAt + REMINDER_MS
            }
            if (track != G7AlarmTrack()) tracks[type] = track
        }
        return G7AlarmStep(G7AlarmRuntime(tracks), actions, next.minOrNull())
    }

    /** The user acknowledged [type], or every alarm going off when it is null. */
    fun acknowledge(runtime: G7AlarmRuntime, config: G7AlarmConfig, type: G7AlarmType?, now: Long): G7AlarmStep {
        val actions = mutableListOf<G7AlarmAction>()
        val tracks = runtime.tracks.mapValues { (t, track) ->
            if (!track.isRaised || (type != null && t != type)) return@mapValues track
            actions += G7AlarmAction.Clear(t)
            val repeat = config[t].repeatMinutes
            if (repeat > 0) track.copy(raisedAt = null, snoozedUntil = now + repeat * 60_000L)
            else track.copy(raisedAt = null, quietUntilCleared = true)
        }
        return G7AlarmStep(G7AlarmRuntime(tracks), actions, tracks.values.mapNotNull { it.snoozedUntil }.minOrNull())
    }

    fun clearAll(runtime: G7AlarmRuntime): G7AlarmStep =
        G7AlarmStep(G7AlarmRuntime(), runtime.raised.map { G7AlarmAction.Clear(it) }, null)

    /** Whether the condition of [type] holds now, before its delay. */
    fun condition(type: G7AlarmType, state: G7State, cfg: G7AlarmTypeConfig, now: Long): Boolean {
        if (!state.isPaired) return false
        val lifecycle = state.lifecycleState(now)
        val algorithm = state.latestAlgorithmState?.let { AlgorithmState(it) }
        val fresh = state.latestReadingAt?.let { now - it <= FRESH_MS } == true
        val glucose = state.latestGlucose?.takeIf { fresh && algorithm?.hasReliableGlucose == true }?.toDouble()
        val rate = state.latestTrendRate?.takeIf { glucose != null && abs(it) <= G7Trend.MAXIMUM_RATE_FOR_ARROW }

        return when (type) {
            G7AlarmType.URGENT_LOW      -> glucose != null && glucose <= G7AlarmType.URGENT_LOW_MGDL
            G7AlarmType.URGENT_LOW_SOON -> glucose != null && rate != null && rate < 0 &&
                glucose + rate * G7AlarmType.URGENT_LOW_SOON_LOOKAHEAD_MINUTES <= G7AlarmType.URGENT_LOW_MGDL

            G7AlarmType.LOW             -> glucose != null && cfg.levelMgdl != null && glucose <= cfg.levelMgdl
            G7AlarmType.HIGH            -> glucose != null && cfg.levelMgdl != null && glucose >= cfg.levelMgdl
            G7AlarmType.FALL_RATE       -> rate != null && cfg.rate != null && rate <= -cfg.rate
            G7AlarmType.RISE_RATE       -> rate != null && cfg.rate != null && rate >= cfg.rate
            G7AlarmType.SENSOR_FAILED   -> lifecycle == G7LifecycleState.FAILED || algorithm?.sensorFailed == true
            G7AlarmType.SENSOR_ENDED    -> lifecycle == G7LifecycleState.EXPIRED
            // Its "for more than" time is counted from the last packet, not from when we noticed.
            G7AlarmType.SIGNAL_LOSS     -> signalLossDueAt(type, state, cfg)?.let { it <= now } == true && lifecycle in RUNNING
            // A packet came, but without a usable value, and the sensor has not failed or ended.
            G7AlarmType.SENSOR_ISSUE    -> fresh && lifecycle in RUNNING && algorithm != null && !algorithm.hasReliableGlucose &&
                !algorithm.isInWarmup && !algorithm.sensorFailed && !algorithm.isSessionEnded &&
                algorithm.state != AlgorithmState.State.STOPPED && algorithm.state != AlgorithmState.State.EXPIRED &&
                algorithm.state != AlgorithmState.State.SESSION_EXPIRED
        }
    }

    /** Signal loss has its delay in [condition] already, counted from the last packet. */
    private fun delayMs(type: G7AlarmType, cfg: G7AlarmTypeConfig): Long =
        if (type == G7AlarmType.SIGNAL_LOSS) 0 else cfg.delayMinutes * 60_000L

    /** When signal loss goes off if nothing arrives: the last packet (or the pairing) plus the delay. */
    private fun signalLossDueAt(type: G7AlarmType, state: G7State, cfg: G7AlarmTypeConfig): Long? {
        if (type != G7AlarmType.SIGNAL_LOSS || !cfg.enabled || !state.isPaired) return null
        val last = listOfNotNull(state.latestReadingAt, state.pairedAt).maxOrNull() ?: return null
        return last + cfg.delayMinutes * 60_000L
    }

    private val RUNNING = setOf(G7LifecycleState.WARMUP, G7LifecycleState.OK, G7LifecycleState.GRACE_PERIOD)
}

/** The night hours for the watch vibration. */
object G7AlarmNight {

    /**
     * True when [minuteOfDay] (0..1439, local time) is in the night from [startHour] to [endHour].
     * The night may run past midnight (22 to 7). The same start and end means there is no night.
     */
    fun isNight(minuteOfDay: Int, startHour: Int, endHour: Int): Boolean {
        val start = startHour * 60
        val end = endHour * 60
        return when {
            start == end -> false
            start < end  -> minuteOfDay in start until end
            else         -> minuteOfDay >= start || minuteOfDay < end
        }
    }
}
