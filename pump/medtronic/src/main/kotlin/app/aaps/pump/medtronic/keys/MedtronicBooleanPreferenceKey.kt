package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.pump.medtronic.R

enum class MedtronicBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val calculatedDefaultValue: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val exportable: Boolean = true,
    override val hideParentScreenIfHidden: Boolean = false,
) : BooleanPreferenceKey {

    SetNeutralTemp(
        key = "set_neutral_temps",
        defaultValue = true,
        titleResId = R.string.set_neutral_temps_title,
        summaryResId = R.string.set_neutral_temps_summary
    ),
}