package app.aaps.pump.aaruy.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

/** Only what the pump cannot tell again after an app restart is kept. What only this phone knows is not exported. */
enum class AaruyLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true,
) : LongNonPreferenceKey {

    /** The start of the last bolus, also the temporary id of its record. */
    PumpBolusStartTime("key_aaruy_bolus_start_time", 0, exportable = false),

    /** Where the next read of the pump log starts. The pump does not know what this phone has read. */
    PumpLastHistoryTime("key_aaruy_last_historyTime", 0, exportable = false),
    PumpLastHistoryDailyTime("key_aaruy_last_historyDailyTime", 0, exportable = false),
    PumpLastDeliveryEnd("key_aaruy_last_delivery_end", 0, exportable = false),

    /** When AAPS last heard of the pump. The pump-unreachable alarm counts from it, also after an app restart. */
    PumpLastConnect("key_aaruy_last_connection", 0, exportable = false),
}
