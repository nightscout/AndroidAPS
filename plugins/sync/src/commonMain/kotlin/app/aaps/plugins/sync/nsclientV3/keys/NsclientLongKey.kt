package app.aaps.plugins.sync.nsclientV3.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

/**
 * How far THIS install has synced with Nightscout. Every entry is a cursor, so the whole enum is
 * `exportable = false`.
 *
 * A cursor says "records up to here are already uploaded". It describes this phone's conversation
 * with the server, not a setting the user chose, and another phone's copy is wrong in both
 * directions: too high and this install silently skips a stretch of records that were never
 * uploaded, too low and it re-sends. Restoring a backup must leave the new phone to work out its own
 * position.
 *
 * If a key is ever added here that is NOT a cursor, give it `exportable = true` explicitly.
 */
enum class NsclientLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = false
) : LongNonPreferenceKey {

    BolusLastSyncedId("ns_bolus_last_synced_id", 0L),
    CarbsLastSyncedId("ns_carbs_last_synced_id", 0L),
    BolusCalculatorLastSyncedId("ns_bolus_calculator_result_last_synced_id", 0L),
    TemporaryTargetLastSyncedId("ns_temporary_target_last_sync", 0L),
    FoodLastSyncedId("ns_food_last_sync", 0L),
    GlucoseValueLastSyncedId("ns_glucose_value_last_sync", 0L),
    CalibrationEntryLastSyncedId("ns_calibration_entry_last_sync", 0L),
    TherapyEventLastSyncedId("ns_therapy_event_last_sync", 0L),
    DeviceStatusLastSyncedId("ns_device_status_last_synced_id", 0L),
    TemporaryBasalLastSyncedId("ns_temporary_basal_last_synced_id", 0L),
    ExtendedBolusLastSyncedId("ns_extended_bolus_last_synced_id", 0L),
    ProfileSwitchLastSyncedId("ns_profile_switch_last_synced_id", 0L),
    EffectiveProfileSwitchLastSyncedId("ns_effective_profile_switch_last_synced_id", 0L),
    RunningModeLastSyncedId("ns_running_mode_last_synced_id", 0L),
    ProfileStoreLastSyncedId("ns_profile_store_last_synced_timestamp", 0L),
}