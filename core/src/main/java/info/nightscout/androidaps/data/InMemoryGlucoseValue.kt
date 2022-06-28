package info.nightscout.androidaps.data

import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.shared.sharedPreferences.SP

class InMemoryGlucoseValue constructor(var timestamp: Long = 0L, var value: Double = 0.0, var smoothed: Double? = null, var interpolated: Boolean = false) {

    constructor(gv: GlucoseValue) : this(gv.timestamp, gv.value, gv.smoothed)
    // var generated : value doesn't correspond to real value with timestamp close to real BG
    private fun useDataSmoothing(sp: SP): Boolean {
        return sp.getBoolean(R.string.key_use_data_smoothing, false)
    }

    fun rawOrSmoothed(sp: SP): Double {
        if (useDataSmoothing(sp)) return smoothed ?: value
        else return value
    }
}