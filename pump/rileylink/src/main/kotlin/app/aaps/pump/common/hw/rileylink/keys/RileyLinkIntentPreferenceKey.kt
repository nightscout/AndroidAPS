package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.pump.common.dialog.RileyLinkBLEConfigActivity
import app.aaps.pump.common.hw.rileylink.R

enum class RileyLinkIntentPreferenceKey(
    override val key: String,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.ACTIVITY,
    override val activityClass: Class<*>? = null,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    MacAddressSelector(
        key = "rileylink_mac_address_selector",
        titleResId = R.string.rileylink_configuration,
        activityClass = RileyLinkBLEConfigActivity::class.java
    )
}