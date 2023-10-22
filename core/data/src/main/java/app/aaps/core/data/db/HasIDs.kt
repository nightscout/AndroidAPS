package app.aaps.core.data.db

interface HasIDs {

    var id: Long
    var version: Int
    var dateCreated: Long
    var isValid: Boolean
    var referenceId: Long?
    var ids: IDs
}