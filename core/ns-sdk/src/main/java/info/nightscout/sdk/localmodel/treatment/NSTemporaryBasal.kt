package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits
import org.json.JSONObject

data class NSTemporaryBasal(
    override val date: Long,
    override val device: String?,
    override val identifier: String,
    override val units: NsUnits?,
    override val srvModified: Long,
    override val srvCreated: Long,
    override val utcOffset: Long,
    override val subject: String?,
    override var isReadOnly: Boolean,
    override val isValid: Boolean,
    override val eventType: EventType,
    override val notes: String?,
    override val pumpId: Long?,
    override val endId: Long?,
    override val pumpType: String?,
    override val pumpSerial: String?,
    val duration: Long,
    val rate: Double,
    val isAbsolute: Boolean,
    val type: Type
) : NSTreatment {

    enum class Type {
        NORMAL,
        EMULATED_PUMP_SUSPEND,
        PUMP_SUSPEND,
        SUPERBOLUS,
        FAKE_EXTENDED // in memory only
        ;

        companion object {

            fun fromString(name: String?) = values().firstOrNull { it.name == name } ?: NORMAL
        }
    }

}
