package info.nightscout.interfaces.iob

import info.nightscout.database.entities.GlucoseValue

class InMemoryGlucoseValue constructor(var timestamp: Long = 0L, var value: Double = 0.0, var interpolated: Boolean = false) {

    constructor(gv: GlucoseValue) : this(gv.timestamp, gv.value)
    // var generated : value doesn't correspond to real value with timestamp close to real BG
}