package app.aaps.pump.common.hw.rileylink.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.IntentPreferenceKey

enum class RileyLinkIntentPreferenceKey(
    override val key: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : IntentPreferenceKey {

    MacAddressSelector("rileylink_mac_address_selector")
}