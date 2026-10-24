package app.aaps.plugins.sync.xdrip.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

/**
 * How far THIS install has synced with xDrip. Every entry is a cursor, so the whole enum is
 * `exportable = false` - same reasoning as `NsclientLongKey`: another phone's position in the
 * upload stream makes this one skip records or re-send them.
 *
 * If a key is ever added here that is NOT a cursor, give it `exportable = true` explicitly.
 */
enum class XdripLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = false
) : LongNonPreferenceKey {

    BolusLastSyncedId("xdrip_bolus_last_synced_id", 0L),
    CarbsLastSyncedId("xdrip_carbs_last_synced_id", 0L),
    BolusCalculatorLastSyncedId("xdrip_bolus_calculator_result_last_synced_id", 0L),
    TemporaryTargetLastSyncedId("xdrip_temporary_target_last_sync", 0L),
    FoodLastSyncedId("xdrip_food_last_sync", 0L),
    GlucoseValueLastSyncedId("xdrip_glucose_value_last_sync", 0L),
    TherapyEventLastSyncedId("xdrip_therapy_event_last_sync", 0L),
    DeviceStatusLastSyncedId("xdrip_device_status_last_synced_id", 0L),
    TemporaryBasalLastSyncedId("xdrip_temporary_basal_last_synced_id", 0L),
    ExtendedBolusLastSyncedId("xdrip_extended_bolus_last_synced_id", 0L),
    ProfileSwitchLastSyncedId("profile_switch_last_synced_id", 0L),
    EffectiveProfileSwitchLastSyncedId("xdrip_effective_profile_switch_last_synced_id", 0L),
    RunningModeLastSyncedId("xdrip_running_mode_last_synced_id", 0L),
    ProfileStoreLastSyncedId("xdrip_profile_store_last_synced_timestamp", 0L),
}
