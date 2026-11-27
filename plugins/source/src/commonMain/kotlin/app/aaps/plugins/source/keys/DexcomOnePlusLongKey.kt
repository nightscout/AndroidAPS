package app.aaps.plugins.source.keys

import app.aaps.core.keys.interfaces.LongNonPreferenceKey

enum class DexcomOnePlusLongKey(
    override val key: String,
    override val defaultValue: Long,
    override val exportable: Boolean = true
) : LongNonPreferenceKey {

    /**
     * Time of the last `SENSOR_CHANGE` this plugin wrote itself, 0 when none.
     *
     * The database does not record who wrote a therapy event. When the sensor later reports its own
     * start, the plugin moves its event; this says which event is its own, so a sensor change logged
     * by hand or synced from Nightscout is never removed. Not exported: it describes this phone's
     * database.
     */
    OwnSensorChangeAt("dexcom_oneplus_own_sensor_change_at", 0L, exportable = false),
}
