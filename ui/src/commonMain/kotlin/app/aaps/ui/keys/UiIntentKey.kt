package app.aaps.ui.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.ui.UiStrings

/** Settings items of `:ui` that open a screen rather than hold a value. */
enum class UiIntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.CLICK,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    /**
     * The alert settings (`_docs/ALARMS.md`, section 6): the pump, the CGM, the BG alarms with their rules, and the app's
     * own alerts, each with how it rings on this phone.
     */
    AlertKinds(key = "alert_kinds", title = UiStrings.alert_kinds_title, summary = UiStrings.alert_kinds_entry_summary),

    /**
     * Android only: opens Android's "Display over other apps" page for AAPS. With it the alarm screen opens while
     * another app is on the screen (#5338).
     */
    AlarmOverOtherApps(key = "alarm_over_other_apps", title = UiStrings.alarm_over_other_apps_title, summary = UiStrings.alarm_over_other_apps_summary)
}
