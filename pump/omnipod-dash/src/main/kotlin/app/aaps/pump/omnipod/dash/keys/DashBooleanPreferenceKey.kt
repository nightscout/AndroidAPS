package app.aaps.pump.omnipod.dash.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey

enum class DashBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : BooleanPreferenceKey {

    SoundUncertainTbrNotification("AAPS.Omnipod.notification_uncertain_tbr_sound_enabled", true),
    SoundUncertainSmbNotification("AAPS.Omnipod.notification_uncertain_smb_sound_enabled", true),
    SoundUncertainBolusNotification("AAPS.Omnipod.notification_uncertain_bolus_sound_enabled", true),
    SoundDeliverySuspendedNotification("AAPS.Omnipod.notification_delivery_suspended_sound_enabled", true),
    UseBonding("AAPS.Omnipod.Dash.use_bonding", false),
}