package info.nightscout.androidaps.database.interfaces

interface DBEntryWithTime {
    var timestamp: Long
    var utcOffset: Long
}