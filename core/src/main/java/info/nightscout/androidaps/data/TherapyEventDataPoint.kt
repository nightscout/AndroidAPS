package info.nightscout.androidaps.data

import android.graphics.Color
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.interfaces.Interval
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.DataPointWithLabelInterface
import info.nightscout.androidaps.plugins.general.overview.graphExtensions.PointsWithLabelGraphSeries
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.Translator
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class TherapyEventDataPoint @Inject constructor(
    val injector: HasAndroidInjector,
    val data: TherapyEvent
) : DataPointWithLabelInterface, Interval {

    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var translator: Translator

    private var yValue = 0.0

    init {
        injector.androidInjector().inject(this)
    }

    override fun getX(): Double {
        return data.timestamp.toDouble()
    }

    override fun getY(): Double {
        val units = profileFunction.getUnits()
        if (data.type == TherapyEvent.Type.NS_MBG) return Profile.fromMgdlToUnits(data.glucose!!, units)
        if (data.glucose != null && data.glucose != 0.0) {
            var mmol = 0.0
            var mgdl = 0.0
            if (units == Constants.MGDL) {
                mgdl = data.glucose!!
                mmol = data.glucose!! * Constants.MGDL_TO_MMOLL
            }
            if (units == Constants.MMOL) {
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
        else translator.translate(data.type.text)

    override fun getDuration(): Long = end() - start()
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
    override fun getColor(): Int =
        when (data.type) {
            TherapyEvent.Type.ANNOUNCEMENT          -> resourceHelper.gc(R.color.notificationAnnouncement)
            TherapyEvent.Type.NS_MBG                -> Color.RED
            TherapyEvent.Type.FINGER_STICK_BG_VALUE -> Color.RED
            TherapyEvent.Type.EXERCISE              -> Color.BLUE
            TherapyEvent.Type.APS_OFFLINE           -> Color.GRAY and -0x7f000001
            else                                    -> Color.GRAY
        }

    // Interval interface
    private var cutEnd: Long? = null

    override fun durationInMsec(): Long = data.duration
    override fun start(): Long = data.timestamp
    override fun originalEnd(): Long = data.timestamp + durationInMsec()
    override fun end(): Long = cutEnd ?: originalEnd()
    override fun cutEndTo(end: Long) {
        cutEnd = end
    }

    override fun match(time: Long): Boolean = start() <= time && end() >= time
    override fun before(time: Long): Boolean = end() < time
    override fun after(time: Long): Boolean = start() > time
    override val isInProgress: Boolean get() = match(System.currentTimeMillis())
    override val isEndingEvent: Boolean get() = durationInMsec() == 0L
    override val isValid: Boolean get() = data.type == TherapyEvent.Type.APS_OFFLINE
}