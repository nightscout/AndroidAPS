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

    /** The list of alert kinds, where the user chooses Ring, Silent or Off for each on this phone (`_docs/ALARMS.md`). */
    AlertKinds(key = "alert_kinds", title = UiStrings.alert_kinds_title, summary = UiStrings.alert_kinds_summary),

    /** The user's own BG level rules (`_docs/ALARMS.md`, step 2a), set on the master. */
    BgAlarmRules(key = "bg_alarm_rules_screen", title = UiStrings.bg_alarm_rules_title, summary = UiStrings.bg_alarm_rules_summary),

    /**
     * Android only: opens Android's "Display over other apps" page for AAPS. With it the alarm screen opens while
     * another app is on the screen (#5338).
     */
    AlarmOverOtherApps(key = "alarm_over_other_apps", title = UiStrings.alarm_over_other_apps_title, summary = UiStrings.alarm_over_other_apps_summary)
}
