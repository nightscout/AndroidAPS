package app.aaps.implementation.alerts.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

/**
 * State of the CGM alarms on THIS phone. Device state, so not exportable, for the same reason as
 * [LocalAlertLongKey]: another phone's alarm state would silence or raise alarms here that this
 * phone has not seen.
 */
enum class LocalAlertStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = false
) : StringNonPreferenceKey {

    /** Where every CGM alarm stands (`CgmAlarmRuntime` as JSON), so an acknowledge survives an app restart. */
    CgmAlarmRuntime("cgmAlarmRuntime", ""),

    /** The BG source the sensor alarms were raised for. When it changes, the sensor alarms start over. */
    CgmAlarmSource("cgmAlarmSource", "")
}
