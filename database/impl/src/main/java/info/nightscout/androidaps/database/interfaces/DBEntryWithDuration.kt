package info.nightscout.androidaps.database.interfaces

interface DBEntryWithDuration {
    var duration: Long

    val durationUnknown get() = duration == Long.MAX_VALUE
}