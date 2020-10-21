package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import info.nightscout.androidaps.db.BgReading

class InMemoryGlucoseValue @JvmOverloads constructor(var timestamp: Long = 0L, var value: Double = 0.0, var interpolated : Boolean = false) {

    constructor(gv: BgReading) : this(gv.date, gv.value)
    // var generated : value doesn't correspond to real value with timestamp close to real BG
}