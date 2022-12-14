package info.nightscout.sdk.localmodel.treatment

import info.nightscout.sdk.localmodel.entry.NsUnits

data class NSTemporaryTarget(
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
    val targetBottom: Double,
    val targetTop: Double,
    val reason: Reason,

    ) : NSTreatment {

    fun targetBottomAsMgdl() = targetBottom.asMgdl()
    fun targetTopAsMgdl() = targetTop.asMgdl()
    enum class Reason(val text: String) {
        CUSTOM("Custom"),
        HYPOGLYCEMIA("Hypo"),
        ACTIVITY("Activity"),
        EATING_SOON("Eating Soon"),
        AUTOMATION("Automation"),
        WEAR("Wear")
        ;

        companion object {

            fun fromString(reason: String?) = values().firstOrNull { it.text == reason } ?: CUSTOM
        }
    }
}
