package app.aaps.ui.compose.alerts

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/** The entries of the CGM alarm settings that open a screen instead of holding a value. */
enum class CgmAlarmIntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.CLICK,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    /** Mute and test, see [CgmAlarmControlsScreen]. */
    Controls(
        key = "cgm_alarm_controls",
        title = CoreUiStrings.cgm_alarms_controls_title,
        summary = CoreUiStrings.cgm_alarms_controls_summary
    )
}
