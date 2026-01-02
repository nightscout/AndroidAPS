package app.aaps.core.nssdk.localmodel.treatment

import app.aaps.core.nssdk.localmodel.entry.NsUnits

data class NSTemporaryTarget(
    override var date: Long?,
    override val device: String? = null,
    override val identifier: String?,
    override val units: NsUnits?,
    override val srvModified: Long? = null,
    override val srvCreated: Long? = null,
    override var utcOffset: Long?,
    override val subject: String? = null,
    override var isReadOnly: Boolean = false,
    override val isValid: Boolean = true,
    override val eventType: EventType,
    override val notes: String? = null,
    override val pumpId: Long?,
    override val endId: Long?,
    override val pumpType: String?,
    override val pumpSerial: String?,
    override var app: String? = null,
    /** Duration in milliseconds */
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

            fun fromString(reason: String?) = Reason.entries.firstOrNull { it.text == reason } ?: CUSTOM
        }
    }
}
