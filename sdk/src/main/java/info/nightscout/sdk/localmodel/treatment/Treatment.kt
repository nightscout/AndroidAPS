package info.nightscout.sdk.localmodel.treatment

interface Treatment {
    val date: Long
    val device: String?
    val identifier: String
    val eventType: EventType
    val srvModified: Long
    val srvCreated: Long
    val utcOffset: Int
    val subject: String?
    var isReadOnly: Boolean // TODO: nullability?
    val isValid: Boolean
}
// TODO: add date string?
