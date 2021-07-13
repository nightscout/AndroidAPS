package info.nightscout.androidaps.plugins.general.overview.graphExtensions

import android.content.Context
import android.graphics.Color
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class TherapyEventDataPoint @Inject constructor(
    val data: TherapyEvent,
    private val resourceHelper: ResourceHelper,
    private val profileFunction: ProfileFunction,
    private val translator: Translator
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double {
        return data.timestamp.toDouble()
    }

    override fun getY(): Double {
        val units = profileFunction.getUnits()
        if (data.type == TherapyEvent.Type.NS_MBG) return Profile.fromMgdlToUnits(data.glucose!!, units)
        if (data.glucose != null && data.glucose != 0.0) {
            var mmol = 0.0
            var mgdl = 0.0
            if (data.glucoseUnit == TherapyEvent.GlucoseUnit.MGDL) {
                mgdl = data.glucose!!
                mmol = data.glucose!! * Constants.MGDL_TO_MMOLL
            }
            if (data.glucoseUnit == TherapyEvent.GlucoseUnit.MMOL) {
                mmol = data.glucose!!
                mgdl = data.glucose!! * Constants.MMOLL_TO_MGDL
            }
            return Profile.toUnits(mgdl, mmol, units)
        }
        return yValue
    }

    override fun setY(y: Double) {
        yValue = y
    }

    override fun getLabel(): String? =
        if (data.note != null) data.note
        else translator.translate(data.type)

    override fun getDuration(): Long = data.duration
    override fun getShape(): PointsWithLabelGraphSeries.Shape =
        when {
            data.type == TherapyEvent.Type.NS_MBG                -> PointsWithLabelGraphSeries.Shape.MBG
            data.type == TherapyEvent.Type.FINGER_STICK_BG_VALUE -> PointsWithLabelGraphSeries.Shape.BGCHECK
            data.type == TherapyEvent.Type.ANNOUNCEMENT          -> PointsWithLabelGraphSeries.Shape.ANNOUNCEMENT
            data.type == TherapyEvent.Type.APS_OFFLINE           -> PointsWithLabelGraphSeries.Shape.OPENAPSOFFLINE
            data.type == TherapyEvent.Type.EXERCISE              -> PointsWithLabelGraphSeries.Shape.EXERCISE
            duration > 0                                         -> PointsWithLabelGraphSeries.Shape.GENERALWITHDURATION
            else                                                 -> PointsWithLabelGraphSeries.Shape.GENERAL
        }

    override fun getSize(): Float = if (resourceHelper.gb(R.bool.isTablet)) 12.0f else 10.0f
    override fun getColor(context: Context): Int =
        when (data.type) {
            TherapyEvent.Type.ANNOUNCEMENT          -> resourceHelper.getAttributeColor(context, R.attr.therapyeventAnnouncement)
            TherapyEvent.Type.NS_MBG                -> resourceHelper.getAttributeColor(context, R.attr.therapyeventNsmbg)
            TherapyEvent.Type.FINGER_STICK_BG_VALUE -> resourceHelper.getAttributeColor(context, R.attr.therapyeventFingerstick)
            TherapyEvent.Type.EXERCISE              -> resourceHelper.getAttributeColor(context, R.attr.therapyeventExercise)
            TherapyEvent.Type.APS_OFFLINE           -> resourceHelper.getAttributeColor(context, R.attr.therapyeventAapsoffline)
            else                                    -> resourceHelper.getAttributeColor(context, R.attr.therapyeventDefault)
        }
}