package app.aaps.core.graph.data

import android.content.Context
import android.graphics.Paint
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.Translator

class TherapyEventDataPoint(
    val data: TE,
    private val rh: ResourceHelper,
    private val profileUtil: ProfileUtil,
    private val translator: Translator
) : DataPointWithLabelInterface {

    private var yValue = 0.0

    override fun getX(): Double = data.timestamp.toDouble()

    override fun getY(): Double {
        if (data.type == TE.Type.NS_MBG) return profileUtil.fromMgdlToUnits(data.glucose!!)
        if (data.glucose != null && data.glucose != 0.0) {
            val mgdl: Double = when (data.glucoseUnit) {
                GlucoseUnit.MGDL -> data.glucose!!
                GlucoseUnit.MMOL -> data.glucose!! * Constants.MMOLL_TO_MGDL
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
                data.type == TE.Type.NS_MBG                -> Shape.MBG
                data.type == TE.Type.FINGER_STICK_BG_VALUE -> Shape.BGCHECK
                data.type == TE.Type.ANNOUNCEMENT          -> Shape.ANNOUNCEMENT
                data.type == TE.Type.SETTINGS_EXPORT       -> Shape.SETTINGS_EXPORT
                data.type == TE.Type.EXERCISE              -> Shape.EXERCISE
                duration > 0                               -> Shape.GENERAL_WITH_DURATION
                else                                       -> Shape.GENERAL
            }
    override val paintStyle: Paint.Style = Paint.Style.FILL // not used

    override val size get() = if (rh.gb(app.aaps.core.ui.R.bool.isTablet)) 12.0f else 10.0f
    override fun color(context: Context?): Int {
        return when (data.type) {
            TE.Type.ANNOUNCEMENT          -> rh.gac(context, app.aaps.core.ui.R.attr.notificationAnnouncement)
            TE.Type.SETTINGS_EXPORT       -> rh.gac(context, app.aaps.core.ui.R.attr.notificationSettingsExport)
            TE.Type.NS_MBG                -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_NS_MBG)
            TE.Type.FINGER_STICK_BG_VALUE -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_FINGER_STICK_BG_VALUE)
            TE.Type.EXERCISE              -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_EXERCISE)
            else                          -> rh.gac(context, app.aaps.core.ui.R.attr.therapyEvent_Default)
        }
    }
}