package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey

enum class AaruyStringKey(
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
    override val isPin: Boolean = false,
    override val exportable: Boolean = true
) : StringPreferenceKey {

    MacAddress("key_aaruy_address", ""),
    PumpName("key_aaruy_name", ""),
    PumpSettings("key_aaruy_pump_settings", ""),
    PumpSwVersion("key_aaruy_sw_version", ""),
    PumpActiveAlarm("key_aaruy_active_alarms", ""),
    PumpActualBasalProfile("key_aaruy_actual_basal_profile", ""),
    PumpBoundAddress("key_binded_address", ""),

    PumpPrefMaxBasal("pref_aaruy_max_basal", "2.0"),
    PumpPrefMaxBolus("pref_aaruy_max_bolus", "10.0"),
    PumpPrefLowAlert("pref_aaruy_low_alert", "20.0"),
}