package info.nightscout.pump.combov2.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import info.nightscout.pump.combov2.R

enum class ComboIntKey(
    override val key: String,
    override val defaultValue: Int,
    private val titleResId: Int,
    override val min: Int = Int.MIN_VALUE,
    override val max: Int = Int.MAX_VALUE,
) : IntPreferenceKey {

    DiscoveryDuration("combov2_bt_discovery_duration", defaultValue = 300, titleResId = R.string.combov2_discovery_duration, min = 30, max = 300),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}
