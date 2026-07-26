package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.pump.medtronic.R

enum class MedtronicIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val entries: Map<Int, Int> = emptyMap(),
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

    MaxBasal(
        key = "pref_medtronic_max_basal",
        defaultValue = 35,
        titleResId = R.string.medtronic_pump_max_basal,
        min = 1,
        max = 35
    ),
    MaxBolus(
        key = "pref_medtronic_max_bolus",
        defaultValue = 25,
        titleResId = R.string.medtronic_pump_max_bolus,
        min = 1,
        max = 25
    ),
    BolusDelay(
        key = "pref_medtronic_bolus_delay",
        defaultValue = 10,
        titleResId = R.string.medtronic_pump_bolus_delay,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            5 to R.string.medtronic_bolus_delay_5s,
            10 to R.string.medtronic_bolus_delay_10s,
            15 to R.string.medtronic_bolus_delay_15s
        ),
        min = 5,
        max = 15
    ),
}