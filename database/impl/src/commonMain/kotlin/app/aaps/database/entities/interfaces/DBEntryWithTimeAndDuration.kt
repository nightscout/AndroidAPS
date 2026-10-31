package app.aaps.database.entities.interfaces

import kotlin.math.min
import kotlin.time.Clock

interface DBEntryWithTimeAndDuration : DBEntryWithTime, DBEntryWithDuration

var DBEntryWithTimeAndDuration.end
    get() = timestamp + duration
    set(value) {
        duration = value - timestamp
        require(duration > 0)
    }

fun DBEntryWithTimeAndDuration.getRemainingDuration(current: Long = Clock.System.now().toEpochMilliseconds()) = min(0L, end - current)