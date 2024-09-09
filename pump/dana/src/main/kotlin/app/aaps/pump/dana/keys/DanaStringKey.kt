package app.aaps.pump.dana.keys

import app.aaps.core.keys.BooleanPreferenceKey
import app.aaps.core.keys.StringPreferenceKey
import app.aaps.pump.dana.R

enum class DanaStringKey(
    override val key: Int,
    override val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : StringPreferenceKey {

    DanaBtName(R.string.key_danar_bt_name, ""),
}
