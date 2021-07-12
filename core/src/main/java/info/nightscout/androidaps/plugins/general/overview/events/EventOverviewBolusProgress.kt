package info.nightscout.androidaps.plugins.general.overview.events

import info.nightscout.androidaps.events.Event

object EventOverviewBolusProgress : Event() {

        data class Treatment constructor(@JvmField var insulin: Double = 0.0, @JvmField var carbs: Int = 0, @JvmField var isSMB: Boolean)

    var status = ""
    var t: Treatment? = null
    var percent = 0

    fun isSMB(): Boolean = t?.isSMB ?: false
}
