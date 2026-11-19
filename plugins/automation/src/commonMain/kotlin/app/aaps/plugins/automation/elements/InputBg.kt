package app.aaps.plugins.automation.elements

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileFunction

class InputBg(profileFunction: ProfileFunction) {

    var units = GlucoseUnit.MGDL
    var value = 0.0
    var minValue = 0.0

    constructor(profileFunction: ProfileFunction, value: Double, units: GlucoseUnit) : this(profileFunction) {
        setUnits(units)
        this.value = value
    }

    init {
        setUnits(profileFunction.getUnits())
        // Start inside the editor's range. 0.0 shows as an empty field, and a condition saved untouched
        // then compared against 0: "below" never fired and "above" always did.
        value = minValue
    }

    fun setValue(value: Double): InputBg {
        this.value = value
        return this
    }

    fun setUnits(units: GlucoseUnit): InputBg {
        minValue = if (units == GlucoseUnit.MMOL) MMOL_MIN else MGDL_MIN
        this.units = units
        return this
    }

    companion object {

        const val MMOL_MIN = 3.0
        const val MMOL_MAX = 20.0
        const val MGDL_MIN = 54.0
        const val MGDL_MAX = 360.0
    }
}
