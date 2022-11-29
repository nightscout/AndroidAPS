package info.nightscout.interfaces.source

import info.nightscout.database.entities.GlucoseValue

interface BgSource {

    /**
     *  Does bg source support advanced filtering ? Currently Dexcom native mode only
     *
     *  @return true if supported
     */
    fun advancedFilteringSupported(): Boolean = false

    /**
     *  Sensor battery level in %
     *
     *  -1 if not supported
     */
    val sensorBatteryLevel: Int
        get() = -1

    /**
     * Decide if GlucoseValue should be uploaded to NS
     *
     * @param glucoseValue glucose value
     * @return true if GlucoseValue should be uploaded to NS (supported by plugin and enabled in preferences)
     */
    fun shouldUploadToNs(glucoseValue: GlucoseValue): Boolean
}