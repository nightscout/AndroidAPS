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

    CARELEVO_PATCH_EXPIRATION_REMINDER_ENABLED(
        "CARELEVO_PATCH_EXPIRATION_REMINDER_ENABLED",
        true,
        R.string.key_carelevo_low_reservoir_reminders
    ),
    CARELEVO_LOW_RESERVOIR_REMINDER_ENABLED(
        "CARELEVO_LOW_RESERVOIR_REMINDER_ENABLED",
        true,
        R.string.carelevo_low_reservoir_reminders_title
    ),
    CARELEVO_BUZZER_REMINDER(
        "CARELEVO_BUZZER_REMINDER",
        false,
        R.string.carelevo_patch_buzzer_alarm_title
    ),
    CARELEVO_CAGE_DEFAULT_APPLIED(
        "carelevo_cage_default_applied",
        false,
        0
    ),
}