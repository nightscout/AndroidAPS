package app.aaps.database.entities.interfaces

interface DBEntryWithTime {

    var timestamp: Long
    var utcOffset: Long
}