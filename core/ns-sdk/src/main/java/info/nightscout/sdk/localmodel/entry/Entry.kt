package info.nightscout.sdk.localmodel.entry

interface Entry {
    val date: Long
    val device: String?
    val identifier: String
    val srvModified: Long
    val srvCreated: Long
    val utcOffset: Long?
    val subject: String?
    var isReadOnly: Boolean // TODO: nullability?
    val isValid: Boolean
}