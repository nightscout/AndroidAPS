package info.nightscout.androidaps.watchfaces.utils

import androidx.viewbinding.ViewBinding
import info.nightscout.androidaps.databinding.ActivityHomeLargeBinding
import info.nightscout.androidaps.databinding.ActivityHome2Binding
import info.nightscout.androidaps.databinding.ActivityHomeBinding
import info.nightscout.androidaps.databinding.ActivityBigchartBinding
import info.nightscout.androidaps.databinding.ActivityCockpitBinding
import info.nightscout.androidaps.databinding.ActivityDigitalstyleBinding
import info.nightscout.androidaps.databinding.ActivityNochartBinding
import info.nightscout.androidaps.databinding.ActivitySteampunkBinding

/**
 * WatchfaceViewAdapter binds all WatchFace variants shared attributes to one common view adapter.
 * Requires at least one of the ViewBinding as a parameter. Recommended to use the factory object to create the binding.
 */
class WatchfaceViewAdapter(
    aL: ActivityHomeLargeBinding? = null,
    a2: ActivityHome2Binding? = null,
    aa: ActivityHomeBinding? = null,
    bC: ActivityBigchartBinding? = null,
    cp: ActivityCockpitBinding? = null,
    ds: ActivityDigitalstyleBinding? = null,
    nC: ActivityNochartBinding? = null,
    sP: ActivitySteampunkBinding? = null
) {

    init {
        if (aL == null && a2 == null && aa == null && bC == null && cp == null && ds == null && nC == null && sP == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }

    private val errorMessage = "Missing require View Binding parameter"
    // Required attributes
    val mainLayout =
        aL?.mainLayout ?: a2?.mainLayout ?: aa?.mainLayout ?: bC?.mainLayout ?: bC?.mainLayout ?: cp?.mainLayout ?: ds?.mainLayout ?: nC?.mainLayout ?: sP?.mainLayout
        ?: throw IllegalArgumentException(errorMessage)
    val timestamp =
        aL?.timestamp ?: a2?.timestamp ?: aa?.timestamp ?: bC?.timestamp ?: bC?.timestamp ?: cp?.timestamp ?: ds?.timestamp ?: nC?.timestamp ?: sP?.timestamp
        ?: throw IllegalArgumentException(errorMessage)
    val root =
        aL?.root ?: a2?.root ?: aa?.root ?: bC?.root ?: bC?.root ?: cp?.root ?: ds?.root ?: nC?.root ?: sP?.root
        ?: throw IllegalArgumentException(errorMessage)

    // Optional attributes
    val sgv = aL?.sgv ?: a2?.sgv ?: aa?.sgv ?: bC?.sgv ?: bC?.sgv ?: cp?.sgv ?: ds?.sgv ?: nC?.sgv
    val direction = aL?.direction ?: a2?.direction ?: aa?.direction ?: cp?.direction ?: ds?.direction
    val loop = a2?.loop ?: cp?.loop ?: sP?.loop
    val delta = aL?.delta ?: a2?.delta ?: aa?.delta ?: bC?.delta ?: bC?.delta ?: cp?.delta ?: ds?.delta ?: nC?.delta
    val avgDelta = a2?.avgDelta ?: bC?.avgDelta ?: bC?.avgDelta ?: cp?.avgDelta ?: ds?.avgDelta ?: nC?.avgDelta
    val uploaderBattery = aL?.uploaderBattery ?: a2?.uploaderBattery ?: aa?.uploaderBattery ?: cp?.uploaderBattery ?: ds?.uploaderBattery ?: sP?.uploaderBattery
    val rigBattery = a2?.rigBattery ?: cp?.rigBattery ?: ds?.rigBattery ?: sP?.rigBattery
    val basalRate = a2?.basalRate ?: cp?.basalRate ?: ds?.basalRate ?: sP?.basalRate
    val bgi = a2?.bgi ?: ds?.bgi
    val AAPSv2 = a2?.AAPSv2 ?: cp?.AAPSv2 ?: ds?.AAPSv2 ?: sP?.AAPSv2
    val cob1 = a2?.cob1 ?: ds?.cob1
    val cob2 = a2?.cob2 ?: cp?.cob2 ?: ds?.cob2 ?: sP?.cob2
    val time = aL?.time ?: a2?.time ?: aa?.time ?: bC?.time ?: bC?.time ?: cp?.time ?: nC?.time
    val minute = ds?.minute
    val hour = ds?.hour
    val day = a2?.day ?: ds?.day
    val month = a2?.month ?: ds?.month
    val iob1 = a2?.iob1 ?: ds?.iob1
    val iob2 = a2?.iob2 ?: cp?.iob2 ?: ds?.iob2 ?: sP?.iob2
    val chart = a2?.chart ?: aa?.chart ?: bC?.chart ?: bC?.chart ?: ds?.chart ?: sP?.chart
    val status = aL?.status ?: aa?.status ?: bC?.status ?: bC?.status ?: nC?.status
    val timePeriod = ds?.timePeriod ?: aL?.timePeriod ?: nC?.timePeriod ?: bC?.timePeriod
    val dayName = ds?.dayName
    val mainMenuTap = ds?.mainMenuTap ?: sP?.mainMenuTap
    val chartZoomTap = ds?.chartZoomTap ?: sP?.chartZoomTap
    val dateTime = ds?.dateTime ?: a2?.dateTime
    // val minuteHand = sP?.minuteHand
    // val secondaryLayout = aL?.secondaryLayout ?: a2?.secondaryLayout ?: aa?.secondaryLayout ?: ds?.secondaryLayout ?: sP?.secondaryLayout
    // val tertiaryLayout = a2?.tertiaryLayout ?: sP?.tertiaryLayout
    // val highLight = cp?.highLight
    // val lowLight = cp?.lowLight
    // val deltaGauge = sP?.deltaPointer
    // val hourHand = sP?.hourHand
    // val glucoseDial = sP?.glucoseDial

    companion object {

        fun getBinding(bindLayout: ViewBinding): WatchfaceViewAdapter {
            return when (bindLayout) {
                is ActivityHomeLargeBinding     -> WatchfaceViewAdapter(bindLayout)
                is ActivityHome2Binding         -> WatchfaceViewAdapter(null, bindLayout)
                is ActivityHomeBinding          -> WatchfaceViewAdapter(null, null, bindLayout)
                is ActivityBigchartBinding      -> WatchfaceViewAdapter(null, null, null, bindLayout)
                is ActivityCockpitBinding       -> WatchfaceViewAdapter(null, null, null, null, bindLayout)
                is ActivityDigitalstyleBinding  -> WatchfaceViewAdapter(null, null, null, null, null,  bindLayout)
                is ActivityNochartBinding       -> WatchfaceViewAdapter(null, null, null, null, null,  null, bindLayout)
                is ActivitySteampunkBinding     -> WatchfaceViewAdapter(null, null, null, null, null, null, null, bindLayout)
                else                            -> throw IllegalArgumentException("ViewBinding is not implement in WatchfaceViewAdapter")
            }
        }
    }

}
