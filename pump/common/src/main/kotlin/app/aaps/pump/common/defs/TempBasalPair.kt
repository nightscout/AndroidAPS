package app.aaps.pump.common.defs

import com.google.gson.annotations.Expose

open class TempBasalPair {

    @Expose var insulinRate = 0.0
    @Expose var durationMinutes = 0
    @Expose var isPercent = false
    private var start: Long? = null
    private var end: Long? = null

    constructor()
    constructor(insulinRate: Double, isPercent: Boolean, durationMinutes: Int) {
        this.insulinRate = insulinRate
        this.isPercent = isPercent
        this.durationMinutes = durationMinutes
    }

    fun setStartTime(startTime: Long?) {
        start = startTime
    }

    fun setEndTime(endTime: Long?) {
        end = endTime
    }

    override fun toString(): String {
        return ("TempBasalPair [" + "Rate=" + insulinRate + ", DurationMinutes=" + durationMinutes + ", IsPercent="
            + isPercent + "]")
    }
}