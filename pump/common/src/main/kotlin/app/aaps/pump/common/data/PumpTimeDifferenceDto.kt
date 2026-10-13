package app.aaps.pump.common.data

import org.joda.time.DateTime
import org.joda.time.Seconds

/**
 * Created by andy on 28/05/2021.
 */
class PumpTimeDifferenceDto(
    var localDeviceTime: DateTime,
    var pumpTime: DateTime
) {

    var timeDifference = 0

    fun calculateDifference() {
        val secondsBetween = Seconds.secondsBetween(localDeviceTime, pumpTime)
        timeDifference = secondsBetween.seconds

        // val diff = localDeviceTime - pumpTime
        // timeDifference = (diff / 1000.0).toInt()
    }

    init {
        calculateDifference()
    }
}