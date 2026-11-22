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
 * What a BG rule looks at (`_docs/ALARMS.md`, steps 2a and 2c). The names are stored with the rules: never change one.
 */
enum class BgRuleType {

    /** The BG past a threshold, low or high. */
    LEVEL,

    /** High above the threshold for a long time while it does not come down, and only with regular readings. HIGH only. */
    PERSISTENT_HIGH,

    /** BG falling ([BgDirection.LOW]) or rising ([BgDirection.HIGH]) faster than a rate per minute. */
    RATE,

    /** The loop predicts BG below the threshold within the next minutes. LOW only. */
    PREDICTED_LOW
}

/**
 * One of the user's own BG rules (`_docs/ALARMS.md`, steps 2a and 2c). Urgent low is not one of them: it is always there
 * and has only its threshold ([app.aaps.core.keys.UnitDoubleKey.AlertUrgentLow]).
 *
 * @param id stable, so an edit keeps the rule's place and its alert
 * @param type what the rule looks at; a rule stored before there were types is a [BgRuleType.LEVEL] rule
 * @param thresholdMgdl always in mg/dl; the screens show the user's units. For a [BgRuleType.RATE] rule it is mg/dl
 *   per minute, always more than 0, and [direction] says whether falling or rising
 * @param severity CRITICAL rings with the alarm screen, WARNING chimes once
 * @param fromMinute start of its time window, minutes after midnight; null with [toMinute] null: all day
 * @param toMinute end of its window; a window may cross midnight (a night rule from 22:00 to 07:00)
 * @param durationMinutes for a level rule and persistent high: how long the readings must stay past the threshold
 *   before it rings (0: at once). For predicted low: how far ahead it looks. Not used by a rate rule
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
    val enabled: Boolean = true,
    val type: BgRuleType = BgRuleType.LEVEL
) {

    init {
        require(type != BgRuleType.PERSISTENT_HIGH || direction == BgDirection.HIGH) { "Persistent high is a high rule" }
        require(type != BgRuleType.PREDICTED_LOW || direction == BgDirection.LOW) { "Predicted low is a low rule" }
        require(type != BgRuleType.RATE || thresholdMgdl > 0.0) { "A rate is more than 0" }
    }

    /** Whether the rule applies at [minuteOfDay] (0..1439). All day without a window. */
    fun appliesAt(minuteOfDay: Int): Boolean {
        val from = fromMinute ?: return true
        val to = toMinute ?: return true
        return if (from <= to) minuteOfDay in from until to else minuteOfDay >= from || minuteOfDay < to
    }
}

/**
 * The user's BG rules: one list for every phone, set on the master or by an Admin client, and synced. Every phone evaluates
 * them on its own readings.
 */
interface BgAlarmRules {

    /** The rules now; a change on the master or from the master comes through here. */
    val rules: StateFlow<List<BgAlarmRule>>

    /**
     * Stores [rules] in place of the old list. On a client the sync sends them to the master, which takes them from an
     * Admin only and sends them to every phone; the screen lets only the master and an Admin client edit.
     */
    fun save(rules: List<BgAlarmRule>)
}
