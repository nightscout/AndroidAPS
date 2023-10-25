package app.aaps.core.main.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.Translator
import app.aaps.database.entities.TherapyEvent

class TherapyEventDataPoint(
    val data: TherapyEvent,
    private val rh: ResourceHelper,
    private val profileUtil: ProfileUtil,
    private val translator: Translator
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()

    override fun getY(): Double {
        if (data.type == TherapyEvent.Type.NS_MBG) return profileUtil.fromMgdlToUnits(data.glucose!!)
        if (data.glucose != null && data.glucose != 0.0) {
            val mgdl: Double = when (data.glucoseUnit) {
                TherapyEvent.GlucoseUnit.MGDL -> data.glucose!!
                TherapyEvent.GlucoseUnit.MMOL -> data.glucose!! * Constants.MMOLL_TO_MGDL
            }
            return profileUtil.fromMgdlToUnits(mgdl)
        }
        return yValue
    }

    override fun setY(y: Double) {
        yValue = y
    }

    override val label get() = if (data.note.isNullOrBlank().not()) data.note!! else translator.translate(data.type)
    override val duration get() = data.duration
    override val shape
        get() =
            when {
                data.type == TherapyEvent.Type.NS_MBG                -> PointsWithLabelGraphSeries.Shape.MBG
                data.type == TherapyEvent.Type.FINGER_STICK_BG_VALUE -> PointsWithLabelGraphSeries.Shape.BGCHECK
                data.type == TherapyEvent.Type.ANNOUNCEMENT          -> PointsWithLabelGraphSeries.Shape.ANNOUNCEMENT
                data.type == TherapyEvent.Type.APS_OFFLINE           -> PointsWithLabelGraphSeries.Shape.OPENAPS_OFFLINE
                data.type == TherapyEvent.Type.EXERCISE              -> PointsWithLabelGraphSeries.Shape.EXERCISE
                duration > 0                                         -> PointsWithLabelGraphSeries.Shape.GENERAL_WITH_DURATION
                else                                                 -> PointsWithLabelGraphSeries.Shape.GENERAL
            }
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used

    override val size get() = if (rh.gb(app.aaps.core.ui.R.bool.isTablet)) 12.0f else 10.0f
    override fun color(context: Context?): Int {
        return when (data.type) {
            TherapyEvent.Type.ANNOUNCEMENT          -> rh.gac(context, app.aaps.core.ui.R.attr.notificationAnnouncement)
            TherapyEvent.Type.NS_MBG                -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_NS_MBG)
            TherapyEvent.Type.FINGER_STICK_BG_VALUE -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_FINGER_STICK_BG_VALUE)
            TherapyEvent.Type.EXERCISE              -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_EXERCISE)
            TherapyEvent.Type.APS_OFFLINE           -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_APS_OFFLINE) and -0x7f000001
            else                                    -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_Default)
        }
    }
}