package app.aaps.pump.carelevo.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.carelevo.R

enum class CarelevoBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int = 0,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    CARELEVO_BUZZER_REMINDER(key = "CARELEVO_BUZZER_REMINDER", defaultValue = false, titleResId = R.string.carelevo_patch_buzzer_alarm_title),
    CARELEVO_CAGE_DEFAULT_APPLIED(key = "carelevo_cage_default_applied", defaultValue = false, titleResId = 0),
}