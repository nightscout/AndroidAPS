package info.nightscout.pump.combov2.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import info.nightscout.pump.combov2.R

enum class ComboIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val titleResId: Int = 0,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
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
) : IntPreferenceKey {

    DiscoveryDuration("combov2_bt_discovery_duration", defaultValue = 300, titleResId = R.string.combov2_discovery_duration, min = 30, max = 300),
}
