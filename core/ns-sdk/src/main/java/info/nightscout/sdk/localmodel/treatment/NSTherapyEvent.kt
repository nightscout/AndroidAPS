package info.nightscout.sdk.localmodel.treatment

import com.google.gson.annotations.SerializedName
import info.nightscout.sdk.localmodel.entry.NsUnits

data class NSTherapyEvent(
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

            fun fromString(text: String?) = values().firstOrNull { it.text == text } ?: MANUAL
        }
    }
}
