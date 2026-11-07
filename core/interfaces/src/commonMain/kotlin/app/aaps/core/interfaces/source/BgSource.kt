package app.aaps.core.interfaces.source

interface BgSource {

    /**
     *  Sensor battery level in %
     *
     *  -1 if not supported
     */
    val sensorBatteryLevel: Int
        get() = -1

    /**
     *  Check if sensor has any error condition (expired, fault, replacement needed, signal lost)
     *
     *  @return true if sensor has error and BG values should not be displayed
     */
    fun hasSensorError(): Boolean = false
}