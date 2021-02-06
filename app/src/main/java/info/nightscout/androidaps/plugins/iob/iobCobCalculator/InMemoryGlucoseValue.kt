package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import info.nightscout.androidaps.database.entities.GlucoseValue

class InMemoryGlucoseValue @JvmOverloads constructor(var timestamp: Long = 0L, var value: Double = 0.0, var interpolated: Boolean = false) {

    constructor(gv: GlucoseValue) : this(gv.timestamp, gv.value)
    // var generated : value doesn't correspond to real value with timestamp close to real BG
}