package info.nightscout.pump.combov2.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import info.nightscout.pump.combov2.R

enum class ComboBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    private val titleResId: Int,
) : BooleanPreferenceKey {

    AutomaticReservoirEntry("combov2_automatic_reservoir_entry", true, titleResId = R.string.combov2_automatic_reservoir_entry),
    AutomaticBatteryEntry("combov2_automatic_battery_entry", true, titleResId = R.string.combov2_automatic_battery_entry),
    VerboseLogging("combov2_verbose_logging", false, titleResId = R.string.combov2_verbose_logging),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}
