package app.aaps.pump.common.hw.rileylink.ble.data

import kotlin.math.abs

class FrequencyTrial {

    var tries = 0
    var successes = 0
    var averageRSSI = 0.0
    var frequencyMHz = 0.0
    var rssiList: ArrayList<Int> = ArrayList()
    var averageRSSI2 = 0.0
    fun calculateAverage() {
        var sum = 0
        var count = 0
        for (rssi in rssiList) {
            sum += abs(rssi)
            count++
        }
        averageRSSI =
            if (count != 0) -sum / count.toDouble()
            else -99.0
    }
}
