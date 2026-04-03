package app.aaps.core.nssdk.localmodel.treatment

import app.aaps.core.nssdk.localmodel.entry.NsUnits
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("__type")
sealed interface NSTreatment {
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
