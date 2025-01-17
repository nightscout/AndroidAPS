package app.aaps.pump.dana.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.StringPreferenceKey

enum class DanaStringKey(
    override val key: String,
    override val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false
) : StringPreferenceKey {

    DanaRName("danar_bt_name", ""),

    DanaRsName("danars_name", ""),
    DanaMacAddress("danars_address", ""),
    DanaRsPassword("danars_password", ""),
}
