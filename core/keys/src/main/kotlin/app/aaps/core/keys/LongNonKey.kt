package app.aaps.core.keys

enum class LongNonKey(
    override val key: String,
    override val defaultValue: Long
) : LongNonPreferenceKey {

    LocalProfileLastChange("local_profile_last_change", 0L),
    BtWatchdogLastBark("bt_watchdog_last", 0L),
    ActivePumpChangeTimestamp("active_pump_change_timestamp", 0L),
    LastCleanupRun("last_cleanup_run", 0L),
}

