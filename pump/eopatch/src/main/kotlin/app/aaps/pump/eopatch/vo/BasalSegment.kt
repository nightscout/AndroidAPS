package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.AppConstant
import com.google.android.gms.common.internal.Preconditions

data class BasalSegment(var start: Long, var end: Long, var doseUnitPerHour: Float) : SegmentEntity<BasalSegment>() {

    override val isEmpty: Boolean
        get() = doseUnitPerHour == 0f

    init {
        Preconditions.checkArgument(start >= 0 && end > 0 && start < end)
        Preconditions.checkArgument(start % 30 == 0L && end % 30 == 0L)
        Preconditions.checkArgument(doseUnitPerHour >= 0)
        this.startMinute = start
        this.endMinute = end
    }

    override fun duplicate(startMinute: Long, endMinute: Long): BasalSegment {
        return BasalSegment(startMinute, endMinute, doseUnitPerHour)
    }

    override fun deep(): BasalSegment {
        return BasalSegment(startMinute, endMinute, doseUnitPerHour)
    }

    override fun apply(segment: JoinedSegment) {
        segment.doseUnitPerHour = doseUnitPerHour
    }

    override fun equalValue(segment: BasalSegment): Boolean {
        return doseUnitPerHour == segment.doseUnitPerHour
    }

    companion object {

        fun create(startMinute: Long, endMinute: Long, doseUnitPerHour: Float): BasalSegment {
            return BasalSegment(startMinute, endMinute, doseUnitPerHour)
        }

        fun create(doseUnitPerHour: Float): BasalSegment {
            return BasalSegment(AppConstant.DAY_START_MINUTE.toLong(), AppConstant.DAY_END_MINUTE.toLong(), doseUnitPerHour)
        }
    }
}
