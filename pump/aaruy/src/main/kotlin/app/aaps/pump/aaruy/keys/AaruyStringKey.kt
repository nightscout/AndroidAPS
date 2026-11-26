package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.R

enum class AaruyStringKey(
    override val key: String,
    override val defaultValue: String,
    private val titleResId: Int? = null,
    private val summaryResId: Int? = null,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
) : StringPreferenceKey {

    MacAddress("key_aaruy_address", ""),
    PumpName("key_aaruy_name", ""),
    PumpSettings("key_aaruy_pump_settings", ""),
    PumpSwVersion("key_aaruy_sw_version", ""),
    PumpActiveAlarm("key_aaruy_active_alarms", ""),
    PumpActualBasalProfile("key_aaruy_actual_basal_profile", ""),
    PumpBoundAddress("key_binded_address", ""),
    ;


    override val title: TextRef = TextRef.AndroidRes(titleResId?:R.string.aaruy_pump_setting)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
    override val isPassword: Boolean = false
    override val isPin: Boolean = false
}