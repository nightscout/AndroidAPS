package app.aaps.pump.omnipod.common.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.omnipod.common.R

enum class DashBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
) : BooleanPreferenceKey {

    SoundDeliverySuspendedNotification("AAPS.Omnipod.notification_delivery_suspended_sound_enabled", true, titleResId = R.string.omnipod_common_preferences_notification_delivery_suspended_sound_enabled),
    UseBonding("AAPS.Omnipod.Dash.use_bonding", false, titleResId = R.string.omnipod_dash_use_bonding, summaryResId = R.string.omnipod_dash_use_bonding_summary),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
