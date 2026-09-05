package app.aaps.pump.equil.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.equil.R

enum class EquilBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
) : BooleanPreferenceKey {

    EquilAlarmBattery("key_equil_alarm_battery", true, titleResId = R.string.equil_settings_alarm_battery),
    EquilAlarmInsulin("key_equil_alarm_insulin", true, titleResId = R.string.equil_settings_alarm_insulin),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}
