package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.database.entities.GlucoseValue

/**
 * Created by mike on 20.06.2016.
 */
interface BgSourceInterface {

    fun advancedFilteringSupported(): Boolean = false
    val sensorBatteryLevel: Int
        get() = -1
    fun uploadToNs(glucoseValue: GlucoseValue) : Boolean
}