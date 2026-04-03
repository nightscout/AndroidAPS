package app.aaps.pump.medtrum.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.pump.medtrum.R

enum class MedtrumStringKey(
    override val key: String,
    override val defaultValue: String,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val entries: Map<String, Int> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val validator: StringValidator = StringValidator.NONE
) : StringPreferenceKey {

    MedtrumAlarmSettings(
        key = "alarm_setting",
        defaultValue = "6",
        titleResId = R.string.alarm_setting_title,
        summaryResId = R.string.alarm_setting_summary,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            "0" to R.string.alarm_setting_light_vibrate_beep,
            "1" to R.string.alarm_setting_light_vibrate,
            "2" to R.string.alarm_setting_light_beep,
            "3" to R.string.alarm_setting_light,
            "4" to R.string.alarm_setting_vibrate_beep,
            "5" to R.string.alarm_setting_vibrate,
            "6" to R.string.alarm_setting_beep,
            "7" to R.string.alarm_setting_silent
        )
    ),
}
