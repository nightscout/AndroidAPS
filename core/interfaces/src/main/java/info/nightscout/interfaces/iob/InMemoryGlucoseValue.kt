package info.nightscout.interfaces.iob

import info.nightscout.database.entities.GlucoseValue

class InMemoryGlucoseValue constructor(var timestamp: Long = 0L, var value: Double = 0.0, var trendArrow: GlucoseValue.TrendArrow = GlucoseValue.TrendArrow.NONE, var smoothed: Double? = null) {

    constructor(gv: GlucoseValue) : this(gv.timestamp, gv.value, gv.trendArrow)

    val recalculated: Double get() = smoothed ?: value
}