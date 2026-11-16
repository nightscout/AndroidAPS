package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.R

enum class AaruyDoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val min: Double,
    override val max: Double,
    private val titleResId: Int? = null,
    private val summaryResId: Int? = null,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
) : DoublePreferenceKey {
    PumpBolusAmount("key_aaruy_bolus_amount_to_be_delivered", 0.0, 0.0, 25.0),
    PumpLastBolusAmount("key_aaruy_last_bolus_amount", 0.0, 0.0, 25.0),
    PumpMaxBasal("key_param_max_basal", 2.0, 0.1, 35.0),
    PumpMaxBolus("key_param_max_bolus", 10.0, 1.0, 25.0),
    PumpLowAlert("key_param_low_alert", 20.0, 10.0, 50.0),

    PumpPrefMaxBasal(
        key = "pref_aaruy_max_basal",
        defaultValue = 2.0,
        titleResId = R.string.setting_insulin_max_basal,
        preferenceType = PreferenceType.TEXT_FIELD,
        min = 0.1,
        max = 35.0
        ),

    PumpPrefMaxBolus(
        key = "pref_aaruy_max_bolus",
        defaultValue = 10.0,
        titleResId = R.string.setting_insulin_max_bolus,
        preferenceType = PreferenceType.TEXT_FIELD,
        min = 1.0,
        max = 25.0
        ),

    PumpPrefLowAlert(
        key = "pref_aaruy_low_alert",
        defaultValue = 20.0,
        titleResId = R.string.setting_insulin_low_reservoir,
        preferenceType = PreferenceType.TEXT_FIELD,
        min = 10.0,
        max = 50.0
    ),
    ;


    override val title: TextRef = TextRef.AndroidRes(titleResId?:R.string.aaruy_pump_setting)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
    override val calculatedBySM: Boolean = false
}