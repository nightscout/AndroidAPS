package app.aaps.core.interfaces.rx.events

object EventOverviewBolusProgress : Event() {

    data class Treatment(var insulin: Double = 0.0, var carbs: Int = 0, var isSMB: Boolean, var id: Long)

    var status = ""
    var t: Treatment? = null
    var percent = 0

    fun isSMB(): Boolean = t?.isSMB ?: false
}