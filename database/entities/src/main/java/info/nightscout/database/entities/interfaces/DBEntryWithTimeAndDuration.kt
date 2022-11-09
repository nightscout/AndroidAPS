package info.nightscout.database.entities.interfaces

import kotlin.math.min

interface DBEntryWithTimeAndDuration : DBEntryWithTime, DBEntryWithDuration

var DBEntryWithTimeAndDuration.end
    get() = timestamp + duration
    set(value) {
        duration = value - timestamp
        require(duration > 0)
    }

fun DBEntryWithTimeAndDuration.getRemainingDuration(current: Long = System.currentTimeMillis()) = min(0L, end - current)