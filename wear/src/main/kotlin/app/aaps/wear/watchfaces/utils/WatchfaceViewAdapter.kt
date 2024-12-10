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
    val tempTarget = cU?.tempTarget
    val reservoir = cU?.reservoir
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
    val loopExt1 = cU?.loopExt1
    val deltaExt1 = cU?.deltaExt1
    val avgDeltaExt1 = cU?.avgDeltaExt1
    val tempTargetExt1 = cU?.tempTargetExt1
    val reservoirExt1 = cU?.reservoirExt1
    val basalRateExt1 = cU?.basalRateExt1
    val bgiExt1 = cU?.bgiExt1
    val cob1Ext1 = cU?.cob1Ext1
    val cob2Ext1 = cU?.cob2Ext1
    val iob1Ext1 = cU?.iob1Ext1
    val iob2Ext1 = cU?.iob2Ext1
    val statusExt1 = cU?.statusExt1
    val rigBatteryExt1 = cU?.rigBatteryExt1
    val patientNameExt1 = cU?.patientNameExt1

    val timestampExt2 = cU?.timestampExt2
    val sgvExt2 = cU?.sgvExt2
    val loopExt2 = cU?.loopExt2
    val deltaExt2 = cU?.deltaExt2
    val avgDeltaExt2 = cU?.avgDeltaExt2
    val tempTargetExt2 = cU?.tempTargetExt2
    val reservoirExt2 = cU?.reservoirExt2
    val basalRateExt2 = cU?.basalRateExt2
    val bgiExt2 = cU?.bgiExt2
    val cob1Ext2 = cU?.cob1Ext2
    val cob2Ext2 = cU?.cob2Ext2
    val iob1Ext2 = cU?.iob1Ext2
    val iob2Ext2 = cU?.iob2Ext2
    val statusExt2 = cU?.statusExt2
    val rigBatteryExt2 = cU?.rigBatteryExt2
    val patientNameExt2 = cU?.patientNameExt2

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

        enum class SelectedWatchFace() {
            NONE,
            CUSTOM,
            DIGITAL,
            CIRCLE;

            companion object {

                fun fromId(ordinal: Int): SelectedWatchFace = SelectedWatchFace.entries[ordinal]
            }
        }
    }

}
