package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits
import org.json.JSONObject

data class NSProfileSwitch(
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
    val profileJson: JSONObject?,
    val profileName: String,
    val originalProfileName: String?,
    val timeShift: Long?,
    val percentage: Int?,
    val duration: Long?,
    val originalDuration: Long?
) : NSTreatment