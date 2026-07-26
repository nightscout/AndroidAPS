package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.omnipod.common.R

enum class OmnipodBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
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

    BolusBeepsEnabled("AAPS.Omnipod.bolus_beeps_enabled", true, titleResId = R.string.omnipod_common_preferences_bolus_beeps_enabled),
    SmbBeepsEnabled("AAPS.Omnipod.smb_beeps_enabled", true, titleResId = R.string.omnipod_common_preferences_smb_beeps_enabled),
    BasalBeepsEnabled("AAPS.Omnipod.basal_beeps_enabled", false, titleResId = R.string.omnipod_common_preferences_basal_beeps_enabled),
    TbrBeepsEnabled("AAPS.Omnipod.tbr_beeps_enabled", false, titleResId = R.string.omnipod_common_preferences_tbr_beeps_enabled),
    ExpirationReminder(
        "AAPS.Omnipod.expiration_reminder_enabled", true,
        titleResId = R.string.omnipod_common_preferences_expiration_reminder_enabled,
        summaryResId = R.string.omnipod_common_preferences_expiration_reminder_enabled_summary
    ),
    ExpirationAlarm(
        "AAPS.Omnipod.expiration_alarm_enabled", true,
        titleResId = R.string.omnipod_common_preferences_expiration_alarm_enabled,
        summaryResId = R.string.omnipod_common_preferences_expiration_alarm_enabled_summary
    ),
    LowReservoirAlert("AAPS.Omnipod.low_reservoir_alert_enabled", true, titleResId = R.string.omnipod_common_preferences_low_reservoir_alert_enabled),
    SoundUncertainTbrNotification("AAPS.Omnipod.notification_uncertain_tbr_sound_enabled", true, titleResId = R.string.omnipod_common_preferences_notification_uncertain_tbr_sound_enabled),
    SoundUncertainSmbNotification("AAPS.Omnipod.notification_uncertain_smb_sound_enabled", true, titleResId = R.string.omnipod_common_preferences_notification_uncertain_smb_sound_enabled),
    SoundUncertainBolusNotification("AAPS.Omnipod.notification_uncertain_bolus_sound_enabled", true, titleResId = R.string.omnipod_common_preferences_notification_uncertain_bolus_sound_enabled),
    AutomaticallyAcknowledgeAlerts("AAPS.Omnipod.automatically_acknowledge_alerts_enabled", false, titleResId = R.string.omnipod_common_preferences_automatically_silence_alerts);

    override val preferenceType: PreferenceType = PreferenceType.SWITCH
}
