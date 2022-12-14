package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits

data class NSOfflineEvent(
    override val date: Long,
    override val device: String?,
    override val identifier: String?,
    override val units: NsUnits?,
    override val srvModified: Long?,
    override val srvCreated: Long?,
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
    override var app: String? = null,
    val duration: Long,
    val reason: Reason
) : NSTreatment {

    enum class Reason {
        DISCONNECT_PUMP,
        SUSPEND,
        DISABLE_LOOP,
        SUPER_BOLUS,
        OTHER
        ;

        companion object {

            fun fromString(reason: String?) = values().firstOrNull { it.name == reason } ?: OTHER
        }
    }
}
