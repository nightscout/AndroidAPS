package app.aaps.plugins.smoothing.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class UkfLongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LastSavedTimestamp("ukf_last_saved_timestamp", 0L),
    LastSensorChangeTimestamp("ukf_sensor_change_timestamp", 0L),
    LastProcessedTimestamp("ukf_last_processed_timestamp", 0L),
}