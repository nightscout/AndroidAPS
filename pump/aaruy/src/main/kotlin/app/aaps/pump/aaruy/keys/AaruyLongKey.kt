package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey

enum class AaruyLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val min: Long = Long.MIN_VALUE,
    override val max: Long = Long.MAX_VALUE,
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
) : LongPreferenceKey {
    PumpBolusStartTime("key_aaruy_bolus_start_time", 0),
    PumpLastBolusTime("key_aaruy_last_bolus_time", 0),
    PumpCurBolusTime("key_aaruy_cur_bolus_time", 0),
    PumpLastConnect("key_aaruy_last_connection", 0),
    PumpLastHistoryTime("key_aaruy_last_historyTime", 0),
    PumpLastHistoryDailyTime("key_aaruy_last_historyDailyTime", 0),
    PumpCgmTime("key_upload_cgms_time", 0),
}