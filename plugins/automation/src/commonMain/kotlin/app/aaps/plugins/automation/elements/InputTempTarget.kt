package app.aaps.plugins.automation.elements

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.profile.ProfileFunction

class InputTempTarget(profileFunction: ProfileFunction) {

    var units: GlucoseUnit = GlucoseUnit.MGDL
    var value = 0.0

    init {
        units = profileFunction.getUnits()
        value = if (units == GlucoseUnit.MMOL) 6.0 else 110.0
    }
}
