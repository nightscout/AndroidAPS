package info.nightscout.androidaps.extensions

import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.androidaps.utils.T.Companion.mins


fun List<EffectiveProfileSwitch>.isEPSEvent5minBack(time: Long): Boolean {
    for (event in this) {
        if (event.timestamp <= time && event.timestamp > time - mins(5).msecs()) {
            if (event.originalDuration == 0L) {
                //aapsLogger.debug(LTag.DATABASE, "Found ProfileSwitch event for time: " + dateUtil.dateAndTimeString(time) + " " + event.toString())
                return true
            }
        }
    }
    return false
}
