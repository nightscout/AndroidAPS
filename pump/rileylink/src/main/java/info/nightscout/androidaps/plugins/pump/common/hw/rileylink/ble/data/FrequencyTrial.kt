package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data

import kotlin.math.abs

class FrequencyTrial {

    @JvmField var tries = 0
    @JvmField var successes = 0
    @JvmField var averageRSSI = 0.0
    @JvmField var frequencyMHz = 0.0
    @JvmField var rssiList: List<Int> = ArrayList()
    @JvmField var averageRSSI2 = 0.0
    fun calculateAverage() {
        var sum = 0
        var count = 0
        for (rssi in rssiList) {
            sum += abs(rssi)
            count++
        }
        averageRSSI =
            if (count != 0) {
                val avg = sum / (count * 1.0)
                avg * -1
            } else -99.0
    }
}
