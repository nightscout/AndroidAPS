package info.nightscout.androidaps.plugins.iob.iobCobCalculator

import info.nightscout.androidaps.db.BgReading

class InMemoryGlucoseValue constructor(var timestamp: Long = 0L, var value: Double = 0.0) {

    constructor(gv: BgReading) : this(gv.date, gv.value)
}