package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey

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

    BolusBeepsEnabled("AAPS.Omnipod.bolus_beeps_enabled", true),
    SmbBeepsEnabled("AAPS.Omnipod.smb_beeps_enabled", true),
    BasalBeepsEnabled("AAPS.Omnipod.basal_beeps_enabled", false),
    TbrBeepsEnabled("AAPS.Omnipod.tbr_beeps_enabled", false),
    ExpirationReminder("AAPS.Omnipod.expiration_reminder_enabled", true),
    ExpirationAlarm("AAPS.Omnipod.expiration_alarm_enabled", true),
    LowReservoirAlert("AAPS.Omnipod.low_reservoir_alert_enabled", true),
    SoundUncertainTbrNotification("AAPS.Omnipod.notification_uncertain_tbr_sound_enabled", true),
    SoundUncertainSmbNotification("AAPS.Omnipod.notification_uncertain_smb_sound_enabled", true),
    SoundUncertainBolusNotification("AAPS.Omnipod.notification_uncertain_bolus_sound_enabled", true),
    AutomaticallyAcknowledgeAlerts("AAPS.Omnipod.automatically_acknowledge_alerts_enabled", false);

    override val preferenceType: PreferenceType = PreferenceType.SWITCH
}
