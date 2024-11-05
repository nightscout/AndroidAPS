package app.aaps.wear.watchfaces.utils

import androidx.viewbinding.ViewBinding
import app.aaps.wear.databinding.ActivityCustomBinding
import app.aaps.wear.databinding.ActivityDigitalstyleBinding

/**
 * WatchfaceViewAdapter binds all WatchFace variants shared attributes to one common view adapter.
 * Requires at least one of the ViewBinding as a parameter. Recommended to use the factory object to create the binding.
 */
class WatchfaceViewAdapter(
    ds: ActivityDigitalstyleBinding? = null,
    cU: ActivityCustomBinding? = null
) {

    init {
        if (ds == null && cU == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }

    private val errorMessage = "Missing require View Binding parameter"

    // Required attributes
    val mainLayout =
        ds?.mainLayout ?: cU?.mainLayout
        ?: throw IllegalArgumentException(errorMessage)
    val timestamp =
        ds?.timestamp ?: cU?.timestamp
        ?: throw IllegalArgumentException(errorMessage)
    val root =
        ds?.root ?: cU?.root
        ?: throw IllegalArgumentException(errorMessage)

    // Optional attributes
    val sgv = ds?.sgv ?: cU?.sgv
    val loop = cU?.loop
    val delta = ds?.delta ?: cU?.delta
    val avgDelta = ds?.avgDelta ?: cU?.avgDelta
    val basalRate = ds?.basalRate ?: cU?.basalRate
    val bgi = ds?.bgi ?: cU?.bgi
    val cob1 = ds?.cob1 ?: cU?.cob1
    val cob2 = ds?.cob2 ?: cU?.cob2
    val iob1 = ds?.iob1 ?: cU?.iob1
    val iob2 = ds?.iob2 ?: cU?.iob2
    val status = cU?.status
    val rigBattery = ds?.rigBattery ?: cU?.rigBattery
    val patientName = cU?.patientName

    val timestampExt1 = cU?.timestampExt1
    val sgvExt1 = cU?.sgvExt1
    val loopExt1 = cU?.loop
    val deltaExt1 = cU?.deltaExt1
    val avgDeltaExt1 = cU?.avgDeltaExt1
    val basalRateExt1 = cU?.basalRateExt1
    val bgiExt1 = cU?.bgiExt1
    val cob1Ext1 = cU?.cob1Ext1
    val cob2Ext1 = cU?.cob2Ext1
    val iob1Ext1 = cU?.iob1Ext1
    val iob2Ext1 = cU?.iob2Ext1
    val statusExt1 = cU?.statusExt1
    val rigBatteryExt1 = cU?.rigBatteryExt1
    val patientNameExt1 = cU?.patientName

    val direction = ds?.direction
    val uploaderBattery = ds?.uploaderBattery ?: cU?.uploaderBattery

    val time = cU?.time
    val second = cU?.second
    val minute = ds?.minute ?: cU?.minute
    val hour = ds?.hour ?: cU?.hour
    val day = ds?.day ?: cU?.day
    val month = ds?.month ?: cU?.month
    val chart = ds?.chart ?: cU?.chart
    val timePeriod = ds?.timePeriod ?: cU?.timePeriod
    val dayName = ds?.dayName ?: cU?.dayName
    val mainMenuTap = ds?.mainMenuTap
    val chartZoomTap = ds?.chartZoomTap
    val dateTime = ds?.dateTime
    val weekNumber = ds?.weekNumber ?: cU?.weekNumber

    companion object {

        fun getBinding(bindLayout: ViewBinding): WatchfaceViewAdapter {
            return when (bindLayout) {
                is ActivityDigitalstyleBinding    -> WatchfaceViewAdapter(bindLayout)
                is ActivityCustomBinding -> WatchfaceViewAdapter(null, bindLayout)
                else                           -> throw IllegalArgumentException("ViewBinding is not implement in WatchfaceViewAdapter")
            }
        }
    }

}
