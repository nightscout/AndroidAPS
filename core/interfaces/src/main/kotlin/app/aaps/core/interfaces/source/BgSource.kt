package app.aaps.core.interfaces.source

interface BgSource {

    /**
     *  Sensor battery level in %
     *
     *  -1 if not supported
     */
    val sensorBatteryLevel: Int
        get() = -1
}