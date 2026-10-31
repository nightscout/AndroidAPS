package app.aaps.core.nssdk.localmodel.treatment

import app.aaps.core.nssdk.localmodel.entry.NsUnits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("NSTemporaryBasal")
data class NSTemporaryBasal(
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
    /** Duration in milliseconds */
    val duration: Long,
    val rate: Double,            // when sending to NS always convertedToAbsolute(timestamp, profile)
    val isAbsolute: Boolean,
    val type: Type,
    val percent: Double? = null, // when sending to NS (rate - 100)
    val absolute: Double? = null, // when sending to NS (rate)
    var extendedEmulated: NSExtendedBolus? = null
) : NSTreatment {

    enum class Type {
        NORMAL,
        EMULATED_PUMP_SUSPEND,
        PUMP_SUSPEND,
        SUPERBOLUS,
        FAKE_EXTENDED // in memory only
        ;

        companion object {

            fun fromString(name: String?) = Type.entries.firstOrNull { it.name == name } ?: NORMAL
        }
    }

}
