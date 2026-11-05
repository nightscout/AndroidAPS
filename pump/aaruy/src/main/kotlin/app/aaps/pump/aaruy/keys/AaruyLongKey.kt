package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class AaruyLongKey(
    override val key: String,
    override val defaultValue: Long,
) : LongNonPreferenceKey {

    PumpBolusStartTime("key_aaruy_bolus_start_time", 0),
    PumpLastBolusTime("key_aaruy_last_bolus_time", 0),
    PumpCurBolusTime("key_aaruy_cur_bolus_time", 0),
    PumpLastConnect("key_aaruy_last_connection", 0),
    PumpLastHistoryTime("key_aaruy_last_historyTime", 0),
    PumpLastHistoryDailyTime("key_aaruy_last_historyDailyTime", 0),
    PumpLastDeliveryEnd("key_aaruy_last_delivery_end", 0),
}
