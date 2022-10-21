package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits

interface Treatment {
    val date: Long
    val device: String?
    val identifier: String
    val units: NsUnits?
    val eventType: EventType
    val srvModified: Long
    val srvCreated: Long
    val utcOffset: Long
    val subject: String?
    var isReadOnly: Boolean
    val isValid: Boolean
    val notes: String?
    val pumpId: Long?
    val pumpType: String?
    val pumpSerial: String?
}
