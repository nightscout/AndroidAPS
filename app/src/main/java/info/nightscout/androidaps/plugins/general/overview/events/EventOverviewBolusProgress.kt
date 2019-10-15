package info.nightscout.androidaps.plugins.general.overview.events

import info.nightscout.androidaps.plugins.treatments.Treatment
import info.nightscout.androidaps.events.Event

object EventOverviewBolusProgress : Event() {
    var status = ""
    var t: Treatment? = null
    var percent = 0

    fun isSMB(): Boolean = t?.isSMB ?: false
}
