package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.TextRef

/**
 * The kinds of the app's own alerts (source [UiInteraction.SOURCE]), as the user sees them in the alert settings
 * (`_docs/ALARMS.md`, section 6, the review of the notification ids). The ids are stored with the user's choice: never
 * change one.
 */
enum class CoreAlert(private val idName: String, override val title: TextRef, override val isProtected: Boolean) : AlertKind {

    /** A command the user started that the pump did not carry out, shown from the overview or the main screen. */
    COMMAND_FAILED("command_failed", InterfacesStrings.alert_kind_command_failed, isProtected = false),

    /** The new insulin is in the pump and the old program runs: the basal is wrong until the write works. */
    INSULIN_CHANGE_FAILED("insulin_change_failed", InterfacesStrings.alert_kind_insulin_change_failed, isProtected = true),

    /** The "time to eat" timer. */
    EAT_REMINDER("eat_reminder", InterfacesStrings.alert_kind_eat_reminder, isProtected = false),

    /** The Alarm action of an automation rule. */
    AUTOMATION_ALARM("automation_alarm", InterfacesStrings.alert_kind_automation_alarm, isProtected = false),

    /**
     * The profile could not be written to the pump, so the pump gives another basal. Not protected: a pump that cannot
     * take every profile (Combo) would ring for something the user knows.
     */
    PROFILE_UPDATE_FAILED("profile_update_failed", InterfacesStrings.alert_kind_profile_update_failed, isProtected = false),

    /** After a profile was set, the pump reports a basal far from it. */
    BASAL_FAR_FROM_PROFILE("basal_far_from_profile", InterfacesStrings.alert_kind_basal_far_from_profile, isProtected = false),

    /** Insulin the user asked for was not given. */
    BOLUS_FAILED("bolus_failed", InterfacesStrings.alert_kind_bolus_failed, isProtected = true),

    /** A bolus was stopped before all of it was given. */
    BOLUS_CANCELLED("bolus_cancelled", InterfacesStrings.alert_kind_bolus_cancelled, isProtected = false),

    /** The bolus was given, but its carbs were not saved: COB and IOB are wrong until they are entered again. */
    CARBS_NOT_SAVED("carbs_not_saved", InterfacesStrings.alert_kind_carbs_not_saved, isProtected = false),

    /** The pump was not reached for longer than the setting allows. Optional: the user turns it on. */
    PUMP_UNREACHABLE("pump_unreachable", InterfacesStrings.alert_kind_pump_unreachable, isProtected = false),

    /** A command has not ended for a long time, so the queue waits: no bolus, no temp basal until it ends. */
    PUMP_DRIVER_NOT_RESPONDING("pump_driver_not_responding", InterfacesStrings.alert_kind_pump_driver_not_responding, isProtected = false),

    /** The running mode stops the insulin, but its zero temp basal is not on the pump: the pump may still give basal. */
    ZERO_DELIVERY_NOT_SET("zero_delivery_not_set", InterfacesStrings.alert_kind_zero_delivery_not_set, isProtected = true),

    /** A value was outside a hard limit and was set to the limit. */
    HARD_LIMIT("hard_limit", InterfacesStrings.alert_kind_hard_limit, isProtected = false),

    /** The loop predicts a low that less insulin cannot prevent: the user has to eat. */
    CARBS_REQUIRED("carbs_required", InterfacesStrings.alert_kind_carbs_required, isProtected = false),

    /**
     * An alarm of a pump driver that has no alert kinds of its own yet and rings through `UiInteraction.runAlarm`.
     * Protected, because the app cannot tell which of those alarms are safe to silence. It goes when the last such
     * driver has moved to alerts.
     */
    PUMP_DRIVER_ALARM("pump_driver_alarm", InterfacesStrings.alert_kind_pump_driver_alarm, isProtected = true);

    override val id: String = "${UiInteraction.SOURCE}.$idName"
    override val source: String get() = UiInteraction.SOURCE
    override val origin: AlertOrigin get() = AlertOrigin.APP

    // The loop writes the announcement of carbs required itself, under its own setting
    override val announced: Boolean get() = this != CARBS_REQUIRED

    /** The key of its alert: one alert of a kind at a time. Raised again while it is active, it does not ring again. */
    val key: String get() = idName

    /**
     * An alert of this kind, with its key, its title and [text] (null: the title says it all). [sound] is the alarm
     * sound of a CRITICAL one, null for the one its severity picks.
     */
    fun alert(severity: AlertSeverity, lifetime: AlertLifetime, text: TextRef?, raisedAt: Long, sound: AlarmSound? = null): Alert =
        Alert(kind = this, key = key, severity = severity, title = title, text = text, raisedAt = raisedAt, lifetime = lifetime, sound = sound)

    /**
     * Raises the alert of a condition of this kind ([AlertLifetime.CONDITION]): it lasts until [resolve]. While it is
     * active it keeps the time it began, so a check that finds the same condition again and again rings once, and a
     * silence the user gave it still holds. [now] is the time of a new one.
     */
    fun raiseCondition(alerts: Alerts, severity: AlertSeverity, text: TextRef?, now: Long, sound: AlarmSound? = null) {
        val since = alerts.active.value.firstOrNull { it.source == source && it.key == key }?.raisedAt ?: now
        alerts.raise(alert(severity, AlertLifetime.CONDITION, text, since, sound))
    }

    /** Raises an event of this kind ([AlertLifetime.EVENT]): each one rings, and the user's answer ends it. */
    fun raiseEvent(alerts: Alerts, severity: AlertSeverity, text: TextRef, now: Long, sound: AlarmSound? = null) =
        alerts.raise(alert(severity, AlertLifetime.EVENT, text, now, sound))

    /** The alert of this kind ends, if there is one. */
    fun resolve(alerts: Alerts) = alerts.resolve(source, key)

    companion object {

        /** The kind of an alarm raised through `UiInteraction.runAlarm` with [key]. */
        fun ofRunAlarmKey(key: String): CoreAlert = when (key) {
            UiInteraction.KEY_COMMAND_FAILED        -> COMMAND_FAILED
            UiInteraction.KEY_INSULIN_CHANGE_FAILED -> INSULIN_CHANGE_FAILED
            UiInteraction.KEY_EAT_REMINDER          -> EAT_REMINDER
            UiInteraction.KEY_AUTOMATION_ALARM      -> AUTOMATION_ALARM
            else                                    -> PUMP_DRIVER_ALARM
        }
    }
}
