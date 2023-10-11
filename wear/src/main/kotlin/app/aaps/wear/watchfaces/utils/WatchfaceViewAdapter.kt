package app.aaps.wear.watchfaces.utils

import androidx.viewbinding.ViewBinding
import app.aaps.wear.databinding.ActivityBigchartBinding
import app.aaps.wear.databinding.ActivityCustomBinding
import app.aaps.wear.databinding.ActivityDigitalstyleBinding
import app.aaps.wear.databinding.ActivityHome2Binding
import app.aaps.wear.databinding.ActivityHomeBinding
import app.aaps.wear.databinding.ActivityHomeLargeBinding
import app.aaps.wear.databinding.ActivityNochartBinding

/**
 * WatchfaceViewAdapter binds all WatchFace variants shared attributes to one common view adapter.
 * Requires at least one of the ViewBinding as a parameter. Recommended to use the factory object to create the binding.
 */
class WatchfaceViewAdapter(
    aL: ActivityHomeLargeBinding? = null,
    a2: ActivityHome2Binding? = null,
    aa: ActivityHomeBinding? = null,
    bC: ActivityBigchartBinding? = null,
    ds: ActivityDigitalstyleBinding? = null,
    nC: ActivityNochartBinding? = null,
    cU: ActivityCustomBinding? = null
) {

    init {
        if (aL == null && a2 == null && aa == null && bC == null && ds == null && nC == null && cU == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }

    private val errorMessage = "Missing require View Binding parameter"

    // Required attributes
    val mainLayout =
        aL?.mainLayout ?: a2?.mainLayout ?: aa?.mainLayout ?: bC?.mainLayout ?: bC?.mainLayout ?: ds?.mainLayout ?: nC?.mainLayout ?: cU?.mainLayout
        ?: throw IllegalArgumentException(errorMessage)
    val timestamp =
        aL?.timestamp ?: a2?.timestamp ?: aa?.timestamp ?: bC?.timestamp ?: bC?.timestamp ?: ds?.timestamp ?: nC?.timestamp ?: cU?.timestamp
        ?: throw IllegalArgumentException(errorMessage)
    val root =
        aL?.root ?: a2?.root ?: aa?.root ?: bC?.root ?: bC?.root ?: ds?.root ?: nC?.root ?: cU?.root
        ?: throw IllegalArgumentException(errorMessage)

    // Optional attributes
    val sgv = aL?.sgv ?: a2?.sgv ?: aa?.sgv ?: bC?.sgv ?: bC?.sgv ?: ds?.sgv ?: nC?.sgv ?: cU?.sgv
    val direction = aL?.direction ?: a2?.direction ?: aa?.direction ?: ds?.direction
    val loop = a2?.loop ?: cU?.loop
    val delta = aL?.delta ?: a2?.delta ?: aa?.delta ?: bC?.delta ?: bC?.delta ?: ds?.delta ?: nC?.delta ?: cU?.delta
    val avgDelta = a2?.avgDelta ?: bC?.avgDelta ?: bC?.avgDelta ?: ds?.avgDelta ?: nC?.avgDelta ?: cU?.avgDelta
    val uploaderBattery = aL?.uploaderBattery ?: a2?.uploaderBattery ?: aa?.uploaderBattery ?: ds?.uploaderBattery ?: cU?.uploaderBattery
    val rigBattery = a2?.rigBattery ?: ds?.rigBattery ?: cU?.rigBattery
    val basalRate = a2?.basalRate ?: ds?.basalRate ?: cU?.basalRate
    val bgi = a2?.bgi ?: ds?.bgi ?: cU?.bgi
    val AAPSv2 = a2?.AAPSv2 ?: ds?.AAPSv2 ?: cU?.AAPSv2
    val cob1 = a2?.cob1 ?: ds?.cob1 ?: cU?.cob1
    val cob2 = a2?.cob2 ?: ds?.cob2 ?: cU?.cob2
    val time = aL?.time ?: a2?.time ?: aa?.time ?: bC?.time ?: bC?.time ?: nC?.time ?: cU?.time
    val second = cU?.second
    val minute = ds?.minute ?: cU?.minute
    val hour = ds?.hour ?: cU?.hour
    val day = a2?.day ?: ds?.day ?: cU?.day
    val month = a2?.month ?: ds?.month ?: cU?.month
    val iob1 = a2?.iob1 ?: ds?.iob1 ?: cU?.iob1
    val iob2 = a2?.iob2 ?: ds?.iob2 ?: cU?.iob2
    val chart = a2?.chart ?: aa?.chart ?: bC?.chart ?: bC?.chart ?: ds?.chart ?: cU?.chart
    val status = aL?.status ?: aa?.status ?: bC?.status ?: bC?.status ?: nC?.status
    val timePeriod = ds?.timePeriod ?: aL?.timePeriod ?: nC?.timePeriod ?: bC?.timePeriod ?: cU?.timePeriod
    val dayName = ds?.dayName ?: cU?.dayName
    val mainMenuTap = ds?.mainMenuTap
    val chartZoomTap = ds?.chartZoomTap
    val dateTime = ds?.dateTime ?: a2?.dateTime
    val weekNumber = ds?.weekNumber ?: cU?.weekNumber
    // val minuteHand = cU?.minuteHand
    // val secondaryLayout = aL?.secondaryLayout ?: a2?.secondaryLayout ?: aa?.secondaryLayout ?: ds?.secondaryLayout
    // val tertiaryLayout = a2?.tertiaryLayout
    // val hourHand = cU?.hourHand

    companion object {

        fun getBinding(bindLayout: ViewBinding): WatchfaceViewAdapter {
            return when (bindLayout) {
                is ActivityHomeLargeBinding    -> WatchfaceViewAdapter(bindLayout)
                is ActivityHome2Binding        -> WatchfaceViewAdapter(null, bindLayout)
                is ActivityHomeBinding         -> WatchfaceViewAdapter(null, null, bindLayout)
                is ActivityBigchartBinding     -> WatchfaceViewAdapter(null, null, null, bindLayout)
                is ActivityDigitalstyleBinding -> WatchfaceViewAdapter(null, null, null, null, bindLayout)
                is ActivityNochartBinding      -> WatchfaceViewAdapter(null, null, null, null, null, bindLayout)
                is ActivityCustomBinding       -> WatchfaceViewAdapter(null, null, null, null, null, null, bindLayout)
                else                           -> throw IllegalArgumentException("ViewBinding is not implement in WatchfaceViewAdapter")
            }
        }
    }

}
