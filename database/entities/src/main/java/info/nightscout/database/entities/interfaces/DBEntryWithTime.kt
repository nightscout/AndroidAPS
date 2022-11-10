package info.nightscout.database.entities.interfaces

interface DBEntryWithTime {
    var timestamp: Long
    var utcOffset: Long
}