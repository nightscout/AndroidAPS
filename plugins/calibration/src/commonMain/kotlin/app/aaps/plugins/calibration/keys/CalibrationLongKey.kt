package app.aaps.plugins.calibration.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class CalibrationLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    /**
     * Calibration entries older than this (epoch ms) are left out of the fit. Set by
     * [app.aaps.core.interfaces.calibration.Calibration.ignoreEntriesBefore]; 0 means no cut.
     *
     * Not exportable: it belongs to the sensor running on this phone. An imported value from
     * another phone, or from an older backup, would hide or bring back the wrong entries.
     */
    EntriesValidFrom("calibration_entries_valid_from", 0L, exportable = false)
}
