package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.TextRef

/**
 * The kinds of the app's own alerts (source [UiInteraction.SOURCE]), as the user sees them in the alert settings.
 *
 * Only the kinds that are alerts today. The core's alarms that are still cards with a sound become kinds here when
 * they move to alerts (`_docs/ALARMS.md`, section 6, the review of the notification ids). The ids are stored with the
 * user's choice: never change one.
 */
enum class CoreAlert(idName: String, override val title: TextRef, override val isProtected: Boolean) : AlertKind {

    /** A command the user started that the pump did not carry out, shown from the overview or the main screen. */
    COMMAND_FAILED("command_failed", InterfacesStrings.alert_kind_command_failed, isProtected = false),

    /** The new insulin is in the pump and the old program runs: the basal is wrong until the write works. */
    INSULIN_CHANGE_FAILED("insulin_change_failed", InterfacesStrings.alert_kind_insulin_change_failed, isProtected = true),

    /** The "time to eat" timer. */
    EAT_REMINDER("eat_reminder", InterfacesStrings.alert_kind_eat_reminder, isProtected = false),

    /** The Alarm action of an automation rule. */
    AUTOMATION_ALARM("automation_alarm", InterfacesStrings.alert_kind_automation_alarm, isProtected = false),

    /**
     * An alarm of a pump driver that has no alert kinds of its own yet and rings through `UiInteraction.runAlarm`.
     * Protected, because the app cannot tell which of those alarms are safe to silence. It goes when the last such
     * driver has moved to alerts.
     */
    PUMP_DRIVER_ALARM("pump_driver_alarm", InterfacesStrings.alert_kind_pump_driver_alarm, isProtected = true);

    override val id: String = "${UiInteraction.SOURCE}.$idName"
    override val source: String get() = UiInteraction.SOURCE
    override val origin: AlertOrigin get() = AlertOrigin.APP

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
