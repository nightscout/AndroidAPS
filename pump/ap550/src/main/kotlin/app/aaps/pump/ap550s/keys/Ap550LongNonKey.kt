package app.aaps.pump.ap550s.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

/**
 * Long values the AP550 driver stores but does not show on its own preference screen.
 * The key strings are the ones the legacy driver already wrote to the SharedPreferences file,
 * so an existing install keeps its values on the first run.
 */
enum class Ap550LongNonKey(
    override val key: String,
    override val defaultValue: Long
) : LongNonPreferenceKey {

    /** Last time the pump TDD was fetched from the pump, as epoch millis. */
    LastPumpTddFetchAttemptTime("last_pump_tdd_fetch_attempt_time", 0L),

    /** Last time the driver asked the pump for its basal profile. */
    LastGetBasalTimestamp("last_get_basal_timestamp", 0L),

    /** Last time the BLE link connected. Legacy raw SharedPreferences key "ap550s_lastConnection". */
    LastConnection("ap550s_lastConnection", 0L),

    /** Last time the reset/fill history was uploaded from the pump, legacy raw key "ap550s_msgHistoryRefill". */
    MsgHistoryRefill("ap550s_msgHistoryRefill", 0L),

    /** Start epoch millis of a temp basal that was running when the link dropped. */
    TempBasalRunningTimestamp("timestamp_temp_basal_running_after_disconnect", 0L),

    /** Epoch millis of the disconnect moment, paired with [TempBasalRunningTimestamp]. */
    TempBasalRunningDisconnectTimestamp("disconnectTimestamp_temp_basal_running_after_disconnect", 0L),

    /** Total duration of the disconnected temp basal, in ms. */
    TempBasalRunningDuration("duration_temp_basal_running_after_disconnect", 0L),
}
