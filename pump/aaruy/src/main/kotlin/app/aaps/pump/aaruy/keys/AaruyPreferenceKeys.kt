package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.R

/** Settings of the pump. They go to the pump at the next status read, see `AaruyPump.syncPrefParam`. */
enum class AaruyDoublePreferenceKey(
    override val key: String,
    override val defaultValue: Double,
    override val min: Double,
    override val max: Double,
    private val titleResId: Int,
    override val calculatedBySM: Boolean = false,
) : DoublePreferenceKey {

    MaxBasal("pref_aaruy_max_basal", 2.0, 1.0, 35.0, R.string.setting_insulin_max_basal),
    MaxBolus("pref_aaruy_max_bolus", 10.0, 1.0, 25.0, R.string.setting_insulin_max_bolus),
    LowReservoir("pref_aaruy_low_alert", 20.0, 10.0, 50.0, R.string.setting_insulin_low_reservoir),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
}

enum class AaruyIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int = 0,
    override val max: Int = 1,
    private val titleResId: Int,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
    private val literalEntries: Map<Int, String> = emptyMap(),
    override val preferenceType: PreferenceType = PreferenceType.LIST,
) : IntPreferenceKey {

    /** 0 is 0.025 U and 1 is 0.05 U each 2 seconds. */
    BolusStep("pref_aaruy_bolus_step", 1, titleResId = R.string.setting_insulin_bolus_rate, literalEntries = mapOf(0 to "0.025", 1 to "0.05")),

    // 0 is on and 1 is off: the value goes to the pump as "close the sound".
    SoundPrompt("pref_aaruy_sound_remind", 0, titleResId = R.string.setting_inject_sound_remind, entriesResIds = mapOf(0 to R.string.setting_aaruy_on, 1 to R.string.setting_aaruy_off)),
    SoundAlarm("pref_aaruy_sound_alarm", 0, titleResId = R.string.setting_inject_sound_alarm, entriesResIds = mapOf(0 to R.string.setting_aaruy_on, 1 to R.string.setting_aaruy_off)),
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val entries: Map<Int, TextRef> = entriesResIds.mapValues<Int, Int, TextRef> { TextRef.AndroidRes(it.value) } + literalEntries.mapValues { TextRef.Literal(it.value) }
}
