package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits

data class NSBolus(
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
    val insulin: Double,
    val type: BolusType

) : NSTreatment {
    enum class BolusType {
        NORMAL,
        SMB,
        PRIMING;

        companion object {

            fun fromString(name: String?) = values().firstOrNull { it.name == name } ?: NORMAL
        }
    }
}
