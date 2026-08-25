package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.omnipod.common.R

enum class DashBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int,
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

    SoundDeliverySuspendedNotification("AAPS.Omnipod.notification_delivery_suspended_sound_enabled", true, titleResId = R.string.omnipod_common_preferences_notification_delivery_suspended_sound_enabled),
    UseBonding("AAPS.Omnipod.Dash.use_bonding", false, titleResId = R.string.omnipod_dash_use_bonding),
}
