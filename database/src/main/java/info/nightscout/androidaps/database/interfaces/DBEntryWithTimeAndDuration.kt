package info.nightscout.androidaps.database.interfaces

import kotlin.math.min

interface DBEntryWithTimeAndDuration : DBEntryWithTime, DBEntryWithDuration

var DBEntryWithTimeAndDuration.end
    get() = timestamp + duration
    set(value) {
        duration = value - timestamp
    }

@JvmOverloads
fun DBEntryWithTimeAndDuration.getRemainingDuration(current: Long = System.currentTimeMillis()) = min(0L, end - current)