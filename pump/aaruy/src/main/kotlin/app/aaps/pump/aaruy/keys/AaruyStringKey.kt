package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class AaruyStringKey(
    override val key: String,
    override val defaultValue: String,
) : StringNonPreferenceKey {

    MacAddress("key_aaruy_address", ""),
    PumpName("key_aaruy_name", ""),
    PumpSettings("key_aaruy_pump_settings", ""),
    PumpSwVersion("key_aaruy_sw_version", ""),
    PumpActiveAlarm("key_aaruy_active_alarms", ""),
    PumpActualBasalProfile("key_aaruy_actual_basal_profile", ""),
    PumpBoundAddress("key_binded_address", ""),
}
