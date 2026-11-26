package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.AaruyStrings

/**
 * Settings of the pump. They go to the pump at the next status read, see `AaruyPump.syncPrefParam`.
 * Max basal and max bolus are no settings: the pump gets the limits of AAPS, see `AaruyPump.maxBasalForPump` and
 * `AaruyPump.maxBolusForPump`.
 */
enum class AaruyDoublePreferenceKey(
    override val key: String,
    override val defaultValue: Double,
    override val min: Double,
    override val max: Double,
    override val title: TextRef,
    override val calculatedBySM: Boolean = false,
) : DoublePreferenceKey {

    LowReservoir("pref_aaruy_low_alert", 20.0, 10.0, 50.0, AaruyStrings.setting_insulin_low_reservoir),
    ;
}

enum class AaruyIntPreferenceKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int = 0,
    override val max: Int = 1,
    override val title: TextRef,
    override val entries: Map<Int, TextRef>,
    override val preferenceType: PreferenceType = PreferenceType.LIST,
) : IntPreferenceKey {

    /** 0 is 0.025 U and 1 is 0.05 U each 2 seconds. */
    BolusStep("pref_aaruy_bolus_step", 1, title = AaruyStrings.setting_insulin_bolus_rate, entries = mapOf(0 to TextRef.Literal("0.025"), 1 to TextRef.Literal("0.05"))),

    // 0 is on and 1 is off: the value goes to the pump as "close the sound".
    SoundPrompt("pref_aaruy_sound_remind", 0, title = AaruyStrings.setting_inject_sound_remind, entries = mapOf(0 to AaruyStrings.setting_aaruy_on, 1 to AaruyStrings.setting_aaruy_off)),
    SoundAlarm("pref_aaruy_sound_alarm", 0, title = AaruyStrings.setting_inject_sound_alarm, entries = mapOf(0 to AaruyStrings.setting_aaruy_on, 1 to AaruyStrings.setting_aaruy_off)),
    ;
}

/** Settings of the driver, not of the pump. */
enum class AaruyBooleanPreferenceKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
) : BooleanPreferenceKey {

    /** Off until the user turns it on: it sends health data to the server of the vendor, see `AaruyHistoryUpload`. */
    UploadHistory("pref_aaruy_upload_history", false, AaruyStrings.setting_upload_history, AaruyStrings.setting_upload_history_summary),
    ;
}
