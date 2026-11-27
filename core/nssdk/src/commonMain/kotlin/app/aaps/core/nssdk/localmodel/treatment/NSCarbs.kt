package app.aaps.core.nssdk.localmodel.treatment

import app.aaps.core.nssdk.localmodel.entry.NsUnits
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@SerialName("NSCarbs")
data class NSCarbs(
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
    override val notes: String?,
    override val pumpId: Long?,
    override val endId: Long?,
    override val pumpType: String?,
    override val pumpSerial: String?,
    override var app: String? = null,
    val carbs: Double,
    /** Duration in milliseconds */
    val duration: Long?,
    /**
     * True when these carbs were read from a Nightscout document that also holds insulin, so an
     * [NSBolus] with the same identifier was read with them. Set only when a document is read.
     * It is never sent to Nightscout.
     */
    @Transient val fromDocumentWithInsulin: Boolean = false
) : NSTreatment