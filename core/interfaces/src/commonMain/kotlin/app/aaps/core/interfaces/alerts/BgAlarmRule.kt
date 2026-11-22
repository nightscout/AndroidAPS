package app.aaps.core.interfaces.alerts

import kotlinx.coroutines.flow.StateFlow

/** Which way a BG level rule looks. */
enum class BgDirection {

    /** Rings below the threshold. */
    LOW,

    /** Rings above the threshold. */
    HIGH
}

/**
 * One of the user's own BG level rules (`_docs/ALARMS.md`, step 2a). Urgent low is not one of them: it is always there
 * and has only its threshold ([app.aaps.core.keys.UnitDoubleKey.AlertUrgentLow]).
 *
 * @param id stable, so an edit keeps the rule's place and its alert
 * @param thresholdMgdl always in mg/dl; the screens show the user's units
 * @param severity CRITICAL rings with the alarm screen, WARNING chimes once
 * @param fromMinute start of its time window, minutes after midnight; null with [toMinute] null: all day
 * @param toMinute end of its window; a window may cross midnight (a night rule from 22:00 to 07:00)
 * @param durationMinutes how long the readings must stay past the threshold before it rings; 0: at once
 * @param silenceMinutes how long Silence lasts when the user does not pick
 */
data class BgAlarmRule(
    val id: String,
    val direction: BgDirection,
    val thresholdMgdl: Double,
    val severity: AlertSeverity = AlertSeverity.WARNING,
    val fromMinute: Int? = null,
    val toMinute: Int? = null,
    val durationMinutes: Int = 0,
    val silenceMinutes: Int = Alerts.SILENCE_MINUTES,
    val enabled: Boolean = true
) {

    /** Whether the rule applies at [minuteOfDay] (0..1439). All day without a window. */
    fun appliesAt(minuteOfDay: Int): Boolean {
        val from = fromMinute ?: return true
        val to = toMinute ?: return true
        return if (from <= to) minuteOfDay in from until to else minuteOfDay >= from || minuteOfDay < to
    }
}

/**
 * The user's BG level rules: set on the master, synced to the clients, which read them only. Every phone evaluates
 * them on its own readings.
 */
interface BgAlarmRules {

    /** The rules now; a change on the master or from the master comes through here. */
    val rules: StateFlow<List<BgAlarmRule>>

    /** Master only: stores [rules] in place of the old list. A client does nothing (the rules are the master's). */
    fun save(rules: List<BgAlarmRule>)
}
