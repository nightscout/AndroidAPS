package app.aaps.core.interfaces.notifications

/**
 * Intent extras that carry an alarm from a notification or an exact alarm into the alarm screen.
 *
 * Lives in core/interfaces so both the activity (ui module) and the alert platform (implementation module) can
 * reference the same keys without a cross-module dependency.
 *
 * It used to carry the alarm itself - its sound, its title, its message, when its notification was posted and
 * whether it was the "time to eat" timer - because `UiInteraction.runAlarm` built the screen from an intent. Every
 * alarm is an alert now and the screen reads it from `Alerts.active`, so the intent only has to say **which**
 * alert. See `_docs/PUMP_DESIGN.md`, section 8.
 */
object AlarmIntent {

    /** Which alert the alarm screen is for: its `source` and `key`. */
    const val EXTRA_ALERT_SOURCE = "alertSource"
    const val EXTRA_ALERT_KEY = "alertKey"

    /**
     * The alert's title and message as they read when the screen was asked for.
     *
     * Not what the screen shows - it takes those from the live alert, so a text the driver changes while the screen
     * is open is the text the user sees. These are here for the system's own preview of a pending intent, and for a
     * log that has to say which alarm an intent was for.
     */
    const val EXTRA_TITLE = "title"
    const val EXTRA_STATUS = "status"
}
