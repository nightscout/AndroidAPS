package app.aaps.core.data.model

interface HasIDs {

    var id: Long
    var version: Int
    var dateCreated: Long
    var isValid: Boolean
    var referenceId: Long?
    var ids: IDs
}