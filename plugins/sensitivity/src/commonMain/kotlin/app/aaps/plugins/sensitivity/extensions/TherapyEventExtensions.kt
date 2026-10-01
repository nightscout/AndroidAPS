package app.aaps.plugins.sensitivity.extensions

import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T

fun List<TE>.isTherapyEventEvent5minBack(time: Long): Boolean {
    for (event in this) {
        if (event.timestamp <= time && event.timestamp > time - T.mins(5).msecs()) {
            return true
        }
    }
    return false
}