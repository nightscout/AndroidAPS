package app.aaps.implementation.alerts.cgm

import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.interfaces.source.CgmSensorAlertState

/**
 * What the glucose alarms read. All glucose values are mg/dL, the value the app shows (calibrated),
 * never the display unit. Times are epoch ms.
 *
 * @param latestMgdl the newest reading, null when there is none.
 * @param latestAt when it was taken.
 * @param ratePerMinute mg/dL per minute over the last minutes, negative when falling, null when
 *   there are not enough readings.
 * @param fallMgdl how much glucose fell within the fall rate window (first reading minus last), null
 *   with fewer than two readings in it.
 * @param riseMgdl how much glucose rose within the rise rate window (last minus first), null with
 *   fewer than two readings in it.
 */
data class CgmGlucoseInput(
    val latestMgdl: Double?,
    val latestAt: Long?,
    val ratePerMinute: Double?,
    val fallMgdl: Double?,
    val riseMgdl: Double?,
)

/** The levels the user set, in mg/dL. */
data class CgmAlarmLevels(
    val lowMgdl: Double,
    val highMgdl: Double,
    val fallDropMgdl: Double,
    val riseMgdl: Double,
)

/**
 * Which alarm conditions hold now, from the glucose and from the sensor state.
 *
 * Pure, so every threshold is tested. The levels have a small band (hysteresis): an alarm whose
 * condition holds stays on until the value has clearly left the alarm zone, so a value that moves
 * around the level does not start a new alarm with every reading.
 */
object CgmAlarmConditions {

    /** Fixed, as in the Dexcom app. Not a setting. */
    const val URGENT_LOW_MGDL: Double = 55.0

    /** Urgent low soon looks this far ahead. */
    const val URGENT_LOW_SOON_MINUTES: Int = 20

    /**
     * A reading older than this no longer counts for the glucose alarms. Allows for two missed
     * readings of a 5 minute sensor. An older value belongs to the signal loss and missed readings
     * alarms.
     */
    const val FRESH_MS: Long = 15 * 60_000L

    /** How long a "warm-up done" message is worth showing. */
    const val WARMUP_DONE_SHOWN_MS: Long = 30 * 60_000L

    const val LOW_HYSTERESIS_MGDL: Double = 5.0
    const val HIGH_HYSTERESIS_MGDL: Double = 10.0

    /**
     * @param held the alarms whose condition held at the previous check, for the hysteresis.
     */
    fun evaluate(
        glucose: CgmGlucoseInput,
        levels: CgmAlarmLevels,
        sensor: CgmSensorAlertState,
        held: Set<CgmAlarmType>,
        now: Long,
    ): Map<CgmAlarmType, CgmAlarmCondition> {
        val result = mutableMapOf<CgmAlarmType, CgmAlarmCondition>()
        glucoseConditions(glucose, levels, held, now).forEach { result[it] = CgmAlarmCondition() }
        sensorCondition(sensor, now)?.let { (type, condition) -> result[type] = condition }
        return result
    }

    fun glucoseConditions(glucose: CgmGlucoseInput, levels: CgmAlarmLevels, held: Set<CgmAlarmType>, now: Long): Set<CgmAlarmType> {
        val at = glucose.latestAt ?: return emptySet()
        val bg = glucose.latestMgdl?.takeIf { now - at <= FRESH_MS } ?: return emptySet()
        val result = mutableSetOf<CgmAlarmType>()

        fun below(type: CgmAlarmType, level: Double) = if (type in held) bg < level + LOW_HYSTERESIS_MGDL else bg <= level
        fun above(type: CgmAlarmType, level: Double) = if (type in held) bg > level - HIGH_HYSTERESIS_MGDL else bg >= level

        if (below(CgmAlarmType.URGENT_LOW, URGENT_LOW_MGDL)) result += CgmAlarmType.URGENT_LOW
        glucose.ratePerMinute?.takeIf { it < 0 }?.let { rate ->
            val projected = bg + rate * URGENT_LOW_SOON_MINUTES
            val limit = if (CgmAlarmType.URGENT_LOW_SOON in held) URGENT_LOW_MGDL + LOW_HYSTERESIS_MGDL else URGENT_LOW_MGDL
            if (projected <= limit) result += CgmAlarmType.URGENT_LOW_SOON
        }
        if (below(CgmAlarmType.LOW, levels.lowMgdl)) result += CgmAlarmType.LOW
        if (above(CgmAlarmType.HIGH, levels.highMgdl)) result += CgmAlarmType.HIGH
        glucose.fallMgdl?.let { drop ->
            // Cleared only when the fall has flattened to half the threshold, as before.
            val limit = if (CgmAlarmType.FALL_RATE in held) levels.fallDropMgdl / 2.0 else levels.fallDropMgdl
            if (if (CgmAlarmType.FALL_RATE in held) drop > limit else drop >= limit) result += CgmAlarmType.FALL_RATE
        }
        glucose.riseMgdl?.let { rise ->
            val limit = if (CgmAlarmType.RISE_RATE in held) levels.riseMgdl / 2.0 else levels.riseMgdl
            if (if (CgmAlarmType.RISE_RATE in held) rise > limit else rise >= limit) result += CgmAlarmType.RISE_RATE
        }
        return result
    }

    /** The one sensor alarm the state stands for, with its episode, or null. */
    fun sensorCondition(state: CgmSensorAlertState, now: Long): Pair<CgmAlarmType, CgmAlarmCondition>? = when (state) {
        CgmSensorAlertState.Ok                   -> null
        is CgmSensorAlertState.Failed            -> CgmAlarmType.SENSOR_FAILED to CgmAlarmCondition(episode = state.reason.name)
        is CgmSensorAlertState.Expired           -> CgmAlarmType.SENSOR_ENDED to CgmAlarmCondition(episode = state.atEpochMs.toString())
        is CgmSensorAlertState.SignalLost        ->
            CgmAlarmType.SIGNAL_LOSS to CgmAlarmCondition(episode = state.sinceEpochMs.toString(), sinceMs = state.sinceEpochMs)

        is CgmSensorAlertState.SensorIssue       ->
            CgmAlarmType.SENSOR_ISSUE to CgmAlarmCondition(episode = state.sinceEpochMs.toString(), sinceMs = state.sinceEpochMs)

        is CgmSensorAlertState.Expiring          -> CgmAlarmType.SENSOR_ENDING_SOON to CgmAlarmCondition(episode = state.endsAtEpochMs.toString())
        is CgmSensorAlertState.SecondSensorReady -> CgmAlarmType.SECOND_SENSOR_READY to CgmAlarmCondition(episode = state.atEpochMs.toString())
        is CgmSensorAlertState.WarmupDone        ->
            if (now - state.atEpochMs < WARMUP_DONE_SHOWN_MS) CgmAlarmType.WARMUP_DONE to CgmAlarmCondition(episode = state.atEpochMs.toString())
            else null
    }
}
