package info.nightscout.sensitivity.extensions

import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.shared.utils.T

fun List<ProfileSwitch>.isPSEvent5minBack(time: Long): Boolean {
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
