package info.nightscout.sensitivity.extensions

import info.nightscout.database.entities.TherapyEvent
import info.nightscout.shared.utils.T

fun List<TherapyEvent>.isTherapyEventEvent5minBack(time: Long): Boolean {
    for (event in this) {
        if (event.timestamp <= time && event.timestamp > time - T.mins(5).msecs()) {
            return true
        }
    }
    return false
}