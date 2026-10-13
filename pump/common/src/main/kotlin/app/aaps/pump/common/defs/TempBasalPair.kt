package app.aaps.pump.common.defs

import app.aaps.pump.common.driver.connector.commands.data.AdditionalResponseDataInterface
import com.google.gson.annotations.Expose

open class TempBasalPair constructor(
    @Expose var insulinRate : Double,
    @Expose var isPercent : Boolean,
    @Expose var durationMinutes : Int,
    @Expose var start: Long? = null ) : AdditionalResponseDataInterface {

    @Expose private var end: Long? = null
    var isActive: Boolean = false
    var id: Long? = null

    init {
        if (start!=null) {
            this.end = start!! + (durationMinutes * 60 * 1000)
        }
    }

    constructor(insulinRate: Double, isPercent: Boolean, durationMinutes: Int):
        this(insulinRate, isPercent, durationMinutes, null)


    // constructor(insulinRate: Double, isPercent: Boolean, durationMinutes: Int) {
    //     this.insulinRate = insulinRate
    //     this.isPercent = isPercent
    //     this.durationMinutes = durationMinutes
    // }

    fun setStartTime(startTime: Long?) {
        start = startTime
    }

    fun setEndTime(endTime: Long?) {
        end = endTime
    }

    override fun toString(): String {
        val unit = if (isPercent) " %" else " U"
        return ("TempBasalPair [rate=${insulinRate}${unit},duration=" + durationMinutes + ",id=$id]")
    }
}