package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits

data class NSBolus(
    override var date: Long?,
    override val device: String?= null,
    override val identifier: String?,
    override val units: NsUnits?= null,
    override val srvModified: Long? = null,
    override val srvCreated: Long? = null,
    override var utcOffset: Long?,
    override val subject: String? = null,
    override var isReadOnly: Boolean = false,
    override val isValid: Boolean,
    override val eventType: EventType,
    override val notes: String?,
    override val pumpId: Long?,
    override val endId: Long?,
    override val pumpType: String?,
    override val pumpSerial: String?,
    override var app: String? = null,
    val insulin: Double,
    val type: BolusType,
    val isBasalInsulin: Boolean

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
