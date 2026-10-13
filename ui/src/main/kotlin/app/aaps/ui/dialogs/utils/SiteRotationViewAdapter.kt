package app.aaps.ui.dialogs.utils

import android.graphics.Color
import android.graphics.PorterDuff
import android.widget.ImageView
import androidx.viewbinding.ViewBinding
import app.aaps.core.data.model.TE
import app.aaps.ui.databinding.DialogSiteRotationManBinding
import app.aaps.ui.databinding.DialogSiteRotationWomanBinding
import app.aaps.ui.databinding.DialogSiteRotationChildBinding
import app.aaps.ui.dialogs.SiteRotationDialog

class SiteRotationViewAdapter(
    val siteRotationDialog: SiteRotationDialog,
    man: DialogSiteRotationManBinding? = null,
    woman: DialogSiteRotationWomanBinding? = null,
    child: DialogSiteRotationChildBinding? = null
) {

    init {
        if (man == null && woman == null && child == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }
    private val errorMessage = "Missing require View Binding parameter"
    val listViews: MutableList<ImageView> = ArrayList()

    // Required attributes
    val root = man?.root ?: woman?.root ?: child?.root ?: throw IllegalArgumentException(errorMessage)
    val front = man?.front ?: woman?.front ?: child?.front ?: throw IllegalArgumentException(errorMessage)
    val back = man?.back ?: woman?.back ?: child?.back ?: throw IllegalArgumentException(errorMessage)

    // Optional attributes
    // FrontView from top to down
    val frontBg = (man?.frontBg ?: woman?.frontBg ?: child?.frontBg)
    val frontLuChest = (man?.frontLuChest)?.also { it.tag = TE.Location.FRONT_LEFT_UPPER_CHEST; listViews.add(it)}
    val fronRuChest = (man?.frontRuChest)?.also { it.tag = TE.Location.FRONT_RIGHT_UPPER_CHEST; listViews.add(it)}
    val sideLAram = (man?.sideLArm ?: woman?.sideLArm)?.also { it.tag = TE.Location.SIDE_LEFT_UPPER_ARM; listViews.add(it)}
    val sideRAram = (man?.sideRArm ?: woman?.sideRArm)?.also { it.tag = TE.Location.SIDE_RIGHT_UPPER_ARM; listViews.add(it)}
    val sideLuAbdomen = (man?.sideLuAbdomen ?: woman?.sideLuAbdomen ?: child?.sideLuAbdomen)?.also { it.tag = TE.Location.SIDE_LEFT_UPPER_ABDOMEN; listViews.add(it)}
    val sideRuAbdomen = (man?.sideRuAbdomen ?: woman?.sideRuAbdomen ?: child?.sideRuAbdomen)?.also { it.tag = TE.Location.SIDE_RIGHT_UPPER_ABDOMEN; listViews.add(it)}
    val frontLuAbdomen = (man?.frontLuAbdomen ?: woman?.frontLuAbdomen ?: child?.frontLuAbdomen)?.also { it.tag = TE.Location.FRONT_LEFT_UPPER_ABDOMEN; listViews.add(it)}
    val frontRuAbdomen = (man?.frontRuAbdomen ?: woman?.frontRuAbdomen ?: child?.frontRuAbdomen)?.also { it.tag = TE.Location.FRONT_RIGHT_UPPER_ABDOMEN; listViews.add(it)}
    val sideLlAbdomen = (man?.sideLlAbdomen ?: woman?.sideLlAbdomen ?: child?.sideLlAbdomen)?.also { it.tag = TE.Location.SIDE_LEFT_LOWER_ABDOMEN; listViews.add(it)}
    val sideRlAbdomen = (man?.sideRlAbdomen ?: woman?.sideRlAbdomen ?: child?.sideRlAbdomen)?.also { it.tag = TE.Location.SIDE_RIGHT_LOWER_ABDOMEN; listViews.add(it)}
    val frontLlAbdomen = (man?.frontLlAbdomen ?: woman?.frontLlAbdomen ?: child?.frontLlAbdomen)?.also { it.tag = TE.Location.FRONT_LEFT_LOWER_ABDOMEN; listViews.add(it)}
    val frontRlAbdomen = (man?.frontRlAbdomen ?: woman?.frontRlAbdomen ?: child?.frontRlAbdomen)?.also { it.tag = TE.Location.FRONT_RIGHT_LOWER_ABDOMEN; listViews.add(it)}
    val frontLuThigh = (man?.frontLuThigh ?: woman?.frontLuThigh ?: child?.frontLuThigh)?.also { it.tag = TE.Location.FRONT_LEFT_UPPER_THIGH; listViews.add(it)}
    val frontRuThigh = (man?.frontRuThigh ?: woman?.frontRuThigh ?: child?.frontRuThigh)?.also { it.tag = TE.Location.FRONT_RIGHT_UPPER_THIGH; listViews.add(it)}
    val frontLlThigh = (man?.frontLlThigh ?: woman?.frontLlThigh ?: child?.frontLlThigh)?.also { it.tag = TE.Location.FRONT_LEFT_LOWER_THIGH; listViews.add(it)}
    val frontRlThigh = (man?.frontRlThigh ?: woman?.frontRlThigh ?: child?.frontRlThigh)?.also { it.tag = TE.Location.FRONT_RIGHT_LOWER_THIGH; listViews.add(it)}

    // BackView from top to down
    val backBg = (man?.backBg ?: woman?.backBg ?: child?.backBg)
    val backLArm = (man?.backLArm ?: woman?.backLArm ?: child?.backLArm)?.also { it.tag = TE.Location.BACK_LEFT_UPPER_ARM; listViews.add(it) }
    val backRArm = (man?.backRArm ?: woman?.backRArm ?: child?.backRArm)?.also { it.tag = TE.Location.BACK_RIGHT_UPPER_ARM; listViews.add(it) }
    val backLButtock = (man?.backLButtock ?: woman?.backLButtock ?: child?.backLButtock)?.also { it.tag = TE.Location.BACK_LEFT_BUTTOCK; listViews.add(it) }
    val backRButtock = (man?.backRButtock ?: woman?.backRButtock ?: child?.backRButtock)?.also { it.tag = TE.Location.BACK_RIGHT_BUTTOCK; listViews.add(it) }
    val sideLuThigh = (man?.sideLuThigh ?: woman?.sideLuThigh)?.also { it.tag = TE.Location.SIDE_LEFT_UPPER_THIGH; listViews.add(it) }
    val sideRuThigh = (man?.sideRuThigh ?: woman?.sideRuThigh)?.also { it.tag = TE.Location.SIDE_RIGHT_UPPER_THIGH; listViews.add(it) }
    val sideLlThigh = (man?.sideLlThigh ?: woman?.sideLlThigh)?.also { it.tag = TE.Location.SIDE_LEFT_LOWER_THIGH; listViews.add(it) }
    val sideRlThigh = (man?.sideRlThigh ?: woman?.sideRlThigh)?.also { it.tag = TE.Location.SIDE_RIGHT_LOWER_THIGH; listViews.add(it) }


    fun updateSiteColors() {
        val listTE = siteRotationDialog.listTE
        val cannula = siteRotationDialog.binding.pumpSiteVisible.isChecked
        val sensor = siteRotationDialog.binding.cgmSiteVisible.isChecked
        // Get filtered and sorted lists
        val cannulaEvents = listTE
            .filter { it.type == TE.Type.CANNULA_CHANGE }
            .sortedByDescending { it.timestamp }

        val sensorEvents = listTE
            .filter { it.type == TE.Type.SENSOR_CHANGE }
            .sortedByDescending { it.timestamp }

        // Create location maps with positions
        val cannulaPositions = cannulaEvents
            .groupBy { it.location }
            .mapValues { (_, events) ->
                cannulaEvents.indexOfFirst { it.timestamp == events.maxOf { it.timestamp } }
            }

        val sensorPositions = sensorEvents
            .groupBy { it.location }
            .mapValues { (_, events) ->
                sensorEvents.indexOfFirst { it.timestamp == events.maxOf { it.timestamp } }
            }

        listViews.forEach { view ->
            val location = view.tag as? TE.Location ?: return@forEach
            // Calculate color fractions (0 = newest, 1 = oldest)
            val cannulaFraction = cannulaPositions[location]?.let { pos ->
                pos.toFloat() / (cannulaEvents.size - 1).coerceAtLeast(14) // Minimum 15 steps
            } ?: 1.5f

            val sensorFraction = sensorPositions[location]?.let { pos ->
                pos.toFloat() / (sensorEvents.size - 1).coerceAtLeast(4) // Minimum 4 steps
            } ?: 1.5f

            // Determine which color to show based on visibility
            when {
                !cannula && !sensor -> {
                    view.clearColorFilter()
                }
                cannula && sensor -> {
                    // Show the "redder" (more recent) of the two colors
                    val color = if (cannulaFraction < sensorFraction) {
                        getSmoothColor(cannulaFraction)
                    } else {
                        getSmoothColor(sensorFraction)
                    }
                    view.setColorFilter(color, PorterDuff.Mode.MULTIPLY)
                }
                cannula -> {
                    view.setColorFilter(getSmoothColor(cannulaFraction), PorterDuff.Mode.MULTIPLY)
                }
                sensor -> {
                    view.setColorFilter(getSmoothColor(sensorFraction), PorterDuff.Mode.MULTIPLY)
                }
            }
        }
        // highlight selected view
        siteRotationDialog.selectedSiteView?.setColorFilter(
            Color.argb(150, 0, 255, 0),
            PorterDuff.Mode.SRC_ATOP
        )
    }

    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val startA = (startColor shr 24) and 0xff
        val startR = (startColor shr 16) and 0xff
        val startG = (startColor shr 8) and 0xff
        val startB = startColor and 0xff

        val endA = (endColor shr 24) and 0xff
        val endR = (endColor shr 16) and 0xff
        val endG = (endColor shr 8) and 0xff
        val endB = endColor and 0xff

        return ((startA + (fraction * (endA - startA)).toInt()) shl 24) or
            ((startR + (fraction * (endR - startR)).toInt()) shl 16) or
            ((startG + (fraction * (endG - startG)).toInt()) shl 8) or
            ((startB + (fraction * (endB - startB)).toInt()))
    }

    private fun getSmoothColor(fraction: Float): Int {
        val colors = listOf(
            Color.RED,                              // Red
            Color.rgb(255, 165, 0), // Orange
            Color.rgb(255, 255, 0), // Yellow
            Color.rgb(146, 200, 80), // Green
            Color.rgb(200, 200, 200) // grey
        )

        return when {
            fraction <= 0f -> colors[0]
            fraction > 1f -> colors[4]
            fraction < 0.25f -> interpolateColor(colors[0], colors[1], fraction / 0.25f)
            fraction < 0.50f -> interpolateColor(colors[1], colors[2], (fraction - 0.25f) / 0.25f)
            else -> interpolateColor(colors[2], colors[3], (fraction - 0.50f) / 0.50f)
        }
    }

    companion object {

        fun getBinding(siteRotationDialog: SiteRotationDialog, bindLayout: ViewBinding): SiteRotationViewAdapter {
            return when (bindLayout) {
                is DialogSiteRotationManBinding   -> SiteRotationViewAdapter(siteRotationDialog, bindLayout)
                is DialogSiteRotationWomanBinding -> SiteRotationViewAdapter(siteRotationDialog, null, bindLayout)
                is DialogSiteRotationChildBinding -> SiteRotationViewAdapter(siteRotationDialog, null, null, bindLayout)
                else                              -> throw IllegalArgumentException("ViewBinding is not implement in WatchfaceViewAdapter")
            }
        }
    }

}