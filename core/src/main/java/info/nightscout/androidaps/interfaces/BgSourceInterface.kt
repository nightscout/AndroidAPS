package info.nightscout.androidaps.interfaces

/**
 * Created by mike on 20.06.2016.
 */
interface BgSourceInterface {

    fun advancedFilteringSupported(): Boolean = false
    val sensorBatteryLevel: Int
        get() = -1
}