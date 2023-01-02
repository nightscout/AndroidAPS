package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits

interface NSTreatment {
    var date: Long?
    val device: String?
    val identifier: String?
    val units: NsUnits?
    val eventType: EventType
    val srvModified: Long?
    val srvCreated: Long?
    var utcOffset: Long?
    val subject: String?
    var isReadOnly: Boolean
    val isValid: Boolean
    val notes: String?
    val pumpId: Long?
    val endId: Long?
    val pumpType: String?
    val pumpSerial: String?
    var app: String?

    fun Double.asMgdl() =
        when (units) {
            NsUnits.MG_DL  -> this
            NsUnits.MMOL_L -> this * 18
            null           -> this
        }
}
