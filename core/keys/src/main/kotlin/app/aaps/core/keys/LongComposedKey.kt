package app.aaps.core.keys

import app.aaps.core.keys.interfaces.LongComposedNonPreferenceKey

enum class LongComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Long
) : LongComposedNonPreferenceKey {

    NotificationSnoozedTo("snoozedTo", "%s", 0L),
    ActivityMonitorStart("Monitor_start_", "%s", 0L),
    ActivityMonitorResumed("Monitor_resumed_", "%s", 0L),
    ActivityMonitorTotal("Monitor_total_", "%s", 0L),
    ;

}