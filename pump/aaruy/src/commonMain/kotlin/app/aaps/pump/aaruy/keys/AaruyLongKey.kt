package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.aaruy.R

enum class AaruyLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val min: Long = Long.MIN_VALUE,
    override val max: Long = Long.MAX_VALUE,
    private val titleResId: Int? = null,
    private val summaryResId: Int? = null,
    private val entriesResIds: Map<Int, Int> = emptyMap(),
) : LongPreferenceKey {
    PumpBolusStartTime("key_aaruy_bolus_start_time", 0),
    PumpLastBolusTime("key_aaruy_last_bolus_time", 0),
    PumpCurBolusTime("key_aaruy_cur_bolus_time", 0),
    PumpLastConnect("key_aaruy_last_connection", 0),
    PumpLastHistoryTime("key_aaruy_last_historyTime", 0),
    PumpLastHistoryDailyTime("key_aaruy_last_historyDailyTime", 0),
    PumpCgmTime("key_upload_cgms_time", 0),
    ;


    override val title: TextRef = TextRef.AndroidRes(titleResId?:R.string.aaruy_pump_setting)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}