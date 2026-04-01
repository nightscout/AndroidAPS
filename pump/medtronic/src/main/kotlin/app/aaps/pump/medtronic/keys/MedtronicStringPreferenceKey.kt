package app.aaps.pump.medtronic.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.StringValidator
import app.aaps.pump.medtronic.R

enum class MedtronicStringPreferenceKey(
    override val key: String,
    override val defaultValue: String,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val entries: Map<String, Int> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true,
    override val validator: StringValidator = StringValidator.NONE
) : StringPreferenceKey {

    Serial(
        key = "pref_medtronic_serial",
        defaultValue = "000000",
        titleResId = R.string.medtronic_serial_number,
        validator = StringValidator.regex("^\\d{6}$", "Must be 6 digits")
    ),
    PumpType(
        key = "pref_medtronic_pump_type",
        defaultValue = "",
        titleResId = R.string.medtronic_pump_type,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            "Other (unsupported)" to R.string.medtronic_pump_type_unsupported,
            "512" to R.string.medtronic_pump_type_512,
            "712" to R.string.medtronic_pump_type_712,
            "515" to R.string.medtronic_pump_type_515,
            "715" to R.string.medtronic_pump_type_715,
            "522" to R.string.medtronic_pump_type_522,
            "722" to R.string.medtronic_pump_type_722,
            "523 (Fw 2.4A or lower)" to R.string.medtronic_pump_type_523_24a,
            "723 (Fw 2.4A or lower)" to R.string.medtronic_pump_type_723_24a,
            "554 (EU Fw. <= 2.6A)" to R.string.medtronic_pump_type_554_eu,
            "754 (EU Fw. <= 2.6A)" to R.string.medtronic_pump_type_754_eu,
            "554 (CA Fw. <= 2.7A)" to R.string.medtronic_pump_type_554_ca,
            "754 (CA Fw. <= 2.7A)" to R.string.medtronic_pump_type_754_ca
        )
    ),
    PumpFrequency(
        key = "pref_medtronic_frequency",
        defaultValue = "medtronic_pump_frequency_us_ca",
        titleResId = R.string.medtronic_pump_frequency,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            "medtronic_pump_frequency_us_ca" to app.aaps.pump.common.hw.rileylink.R.string.medtronic_pump_frequency_us_ca,
            "medtronic_pump_frequency_worldwide" to app.aaps.pump.common.hw.rileylink.R.string.medtronic_pump_frequency_worldwide
        )
    ),
    BatteryType(
        key = "pref_medtronic_battery_type",
        defaultValue = app.aaps.pump.medtronic.defs.BatteryType.None.key,
        titleResId = R.string.medtronic_pump_battery_select,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            app.aaps.pump.medtronic.defs.BatteryType.None.key to R.string.medtronic_pump_battery_no,
            app.aaps.pump.medtronic.defs.BatteryType.Alkaline.key to R.string.medtronic_pump_battery_alkaline,
            app.aaps.pump.medtronic.defs.BatteryType.Lithium.key to R.string.medtronic_pump_battery_lithium,
            app.aaps.pump.medtronic.defs.BatteryType.NiZn.key to R.string.medtronic_pump_battery_nizn,
            app.aaps.pump.medtronic.defs.BatteryType.NiMH.key to R.string.medtronic_pump_battery_nimh
        )
    ),
}