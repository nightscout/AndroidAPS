package app.aaps.core.interfaces.source

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
     *  Check if sensor has any error condition (expired, fault, replacement needed, signal lost)
     *
     *  @return true if sensor has error and BG values should not be displayed
     */
    fun hasSensorError(): Boolean = false  // ← 默认返回false
}