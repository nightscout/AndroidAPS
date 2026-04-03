package app.aaps.core.nssdk.localmodel.treatment

import app.aaps.core.nssdk.localmodel.entry.NsUnits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("NSOfflineEvent")
data class NSOfflineEvent(
    override var date: Long?,
    override val device: String? = null,
    override val identifier: String?,
    override val units: NsUnits? = null,
    override val srvModified: Long? = null,
    override val srvCreated: Long? = null,
    override var utcOffset: Long?,
    override val subject: String? = null,
    override var isReadOnly: Boolean = false,
    override val isValid: Boolean,
    override val eventType: EventType,
    override val notes: String? = null,
    override val pumpId: Long?,
    override val endId: Long?,
    override val pumpType: String?,
    override val pumpSerial: String?,
    override var app: String? = null,
    /**
     * Duration in milliseconds
     * Can be fake zero in RM mode
     */
    val duration: Long,
    /** RM Duration in milliseconds */
    val originalDuration: Long?,
    val reason: Reason, // Used in v1 only
    val mode: Mode, // Used in RM
    val autoForced: Boolean, // Used in RM
    val reasons: String?, // Used in RM
) : NSTreatment {

    enum class Mode {
        DISABLED_LOOP,
        OPEN_LOOP,
        CLOSED_LOOP,
        CLOSED_LOOP_LGS,
        // Temporary only
        SUPER_BOLUS,
        DISCONNECTED_PUMP,
        SUSPENDED_BY_PUMP,
        SUSPENDED_BY_USER,
        UNKNOWN
        ;

        companion object {

            fun fromString(reason: String?) = Mode.entries.firstOrNull { it.name == reason } ?: UNKNOWN
        }
    }

    enum class Reason {
        DISCONNECT_PUMP,
        SUSPEND,
        DISABLE_LOOP,
        SUPER_BOLUS,
        OTHER
        ;

        companion object {

            fun fromString(reason: String?) = Reason.entries.firstOrNull { it.name == reason } ?: OTHER
        }
    }
}
