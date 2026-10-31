package app.aaps.plugins.smoothing.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class UkfLongNonKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    LastSavedTimestamp("ukf_last_saved_timestamp", 0L),
    LastSensorChangeTimestamp("ukf_sensor_change_timestamp", 0L),

    /**
     * How far THIS phone's filter has processed, so it is not exported - another phone's copy would
     * make this one skip readings. Same reason as [app.aaps.plugins.source.keys.GlunovoLongKey].
     *
     * The two above are deliberately left as they are: they were not part of the change that made this
     * one non-exportable, and whether install-local filter state should travel at all is a
     * classification question for 4.2 step 2, not a decision to take in passing.
     */
    LastProcessedTimestamp("ukf_last_processed_timestamp", 0L, exportable = false),
}