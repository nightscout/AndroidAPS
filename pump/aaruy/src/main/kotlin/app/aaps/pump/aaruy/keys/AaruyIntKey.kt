package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.R

enum class AaruyIntKey(
    override val key: String,
    override val defaultValue: Int,
    private val titleResId: Int? = null,
    private val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
    override val min: Int,
    override val max: Int,
) : IntPreferenceKey {
    PumpState(
        key = "key_aaruy_pump_state",
        defaultValue = 125,
        titleResId = R.string.pump_state_label,
        min = 0,
        max = 255
    ),

    PumpLastState(
        key = "key_aaruy_pump_last_state",
        titleResId = R.string.pump_state_label,
        defaultValue = 0,
        min = 0,
        max = 255),

    PumpBolusStep(
        key = "key_param_bolus_step",
        titleResId = R.string.setting_insulin_bolus_rate,
        preferenceType = PreferenceType.LIST,
        entriesResIds = mapOf(
            1 to R.string.unit_u_0_025,
            2 to R.string.unit_u_0_05
        ),
        defaultValue = 2,
        min = 1,
        max = 15
    ),

    PumpCloseInjectSound(
        key = "key_param_close_inject_sound",
        titleResId = R.string.setting_inject_sound_remind,
        defaultValue = 0,
        min = 0,
        max = 15
    ),

    PumpNeedleType(
        key = "key_param_soft_needle_type",
        defaultValue = 0,
        min = 0,
        max = 1),

    PumpType(
        key = "key_param_pump_type",
        defaultValue = 0,
        min = 0,
        max = 8
    ),

    PumpUploadIndex(
        key = "key_upload_index",
        defaultValue = 0,
        min = 0,
        max = 0xfffffff
    ),

    PumpUploadLastIndex(
        key = "key_upload_lastIndex",
        defaultValue = 0,
        min = 0,
        max = 0xfffffff
    ),


    PumpPrefBolusStep(
        key = "pref_aaruy_bolus_step",
        titleResId = R.string.setting_insulin_bolus_rate,
        defaultValue = 1,
        preferenceType = PreferenceType.LIST,
        entriesResIds = mapOf(
            0 to R.string.unit_u_0_025,
            1 to R.string.unit_u_0_05
        ),
        min = 0,
        max = 1
    ),

    PumpPrefSoundRemind(
        key = "pref_aaruy_sound_remind",
        defaultValue = 1,
        preferenceType = PreferenceType.LIST,
        titleResId = R.string.setting_inject_sound_remind,
        entriesResIds = mapOf(
            0 to R.string.setting_aaruy_off,
            1 to R.string.setting_aaruy_on
        ),
        min = 0,
        max = 1
    ),
    PumpPrefSoundAlarm(
        key = "pref_aaruy_sound_alarm",
        titleResId = R.string.setting_inject_sound_alarm,
        defaultValue = 1,
        preferenceType = PreferenceType.LIST,
        entriesResIds = mapOf(
            0 to R.string.setting_aaruy_off,
            1 to R.string.setting_aaruy_on
        ),
        min = 0,
        max = 1
    ),
    ;


    override val title: TextRef = TextRef.AndroidRes(titleResId?:R.string.aaruy_pump_setting)
    override val entries: Map<Int, TextRef> = entriesResIds.mapValues { TextRef.AndroidRes(it.value) }
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}