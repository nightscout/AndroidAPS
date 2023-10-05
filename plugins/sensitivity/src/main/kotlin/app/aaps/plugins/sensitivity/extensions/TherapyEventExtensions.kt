package app.aaps.plugins.sensitivity.extensions

import app.aaps.core.data.time.T
import app.aaps.database.entities.TherapyEvent

fun List<TherapyEvent>.isTherapyEventEvent5minBack(time: Long): Boolean {
    for (event in this) {
        if (event.timestamp <= time && event.timestamp > time - T.mins(5).msecs()) {
            return true
        }
    }
    return false
}