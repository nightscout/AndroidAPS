package app.aaps.plugins.sensitivity.extensions

import app.aaps.core.data.model.PS
import app.aaps.core.data.time.T

fun List<PS>.isPSEvent5minBack(time: Long): Boolean {
    for (event in this) {
        if (event.timestamp <= time && event.timestamp > time - T.mins(5).msecs()) {
            if (event.duration == 0L) {
                //aapsLogger.debug(LTag.DATABASE, "Found ProfileSwitch event for time: " + dateUtil.dateAndTimeString(time) + " " + event.toString())
                return true
            }
        }
    }
    return false
}
