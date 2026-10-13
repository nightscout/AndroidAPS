package app.aaps.core.nssdk.localmodel.treatment

import app.aaps.core.nssdk.localmodel.entry.NsUnits
import com.google.gson.annotations.SerializedName

data class NSTherapyEvent(
    override var date: Long?,
    override val device: String? = null,
    override val identifier: String?,
    override val units: NsUnits?,
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
    /** Duration in milliseconds */
    var location: String? = null,
    var arrow: String? = null,
    val duration: Long,
    var enteredBy: String? = null,
    var glucose: Double? = null,
    var glucoseType: MeterType? = null,
) : NSTreatment {

    enum class MeterType(val text: String) {
        @SerializedName("Finger") FINGER("Finger"),
        @SerializedName("Sensor") SENSOR("Sensor"),
        @SerializedName("Manual") MANUAL("Manual")
        ;

        companion object {

            fun fromString(text: String?) = MeterType.entries.firstOrNull { it.text == text }
        }
    }
}
