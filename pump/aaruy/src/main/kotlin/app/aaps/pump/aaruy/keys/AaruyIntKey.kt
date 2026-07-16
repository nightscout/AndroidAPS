package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey

enum class AaruyIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    override val defaultedBySM: Boolean = false,
    override val calculatedDefaultValue: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val exportable: Boolean = true
) : IntPreferenceKey {
    PumpState("key_aaruy_pump_state", 125, 0, 255),
    PumpLastState("key_aaruy_pump_last_state", 0, 0, 255),
    PumpBolusStep("key_param_bolus_step", 2, 1, 15),
    PumpCloseInjectSound("key_param_close_inject_sound", 0, 0, 15),
    PumpNeedleType("key_param_soft_needle_type", 0, 0, 1),
    PumpType("key_param_pump_type", 0, 0, 8),
    PumpUploadIndex("key_upload_index", 0, 0, 0xfffffff),
    PumpUploadLastIndex("key_upload_lastIndex", 0, 0, 0xfffffff),

    PumpPrefBolusStep("pref_aaruy_bolus_step", 1, 0, 1),
    PumpPrefSoundRemind("pref_aaruy_sound_remind", 1, 0, 1),
    PumpPrefSoundAlarm("pref_aaruy_sound_alarm", 1, 0, 1),
}