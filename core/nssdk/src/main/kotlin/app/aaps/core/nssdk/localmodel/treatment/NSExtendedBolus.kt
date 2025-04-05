package app.aaps.core.nssdk.localmodel.treatment

import app.aaps.core.nssdk.localmodel.entry.NsUnits

data class NSExtendedBolus(
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
    val enteredinsulin: Double,
    val isEmulatingTempBasal: Boolean?,
    val rate: Double?
) : NSTreatment
