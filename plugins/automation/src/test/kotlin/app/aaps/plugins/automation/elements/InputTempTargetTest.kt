package app.aaps.plugins.automation.elements

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.plugins.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputTempTargetTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputTempTarget(profileFunction)
        i.units = GlucoseUnit.MMOL
        i.value = 5.0
        Assertions.assertEquals(5.0, i.value, 0.01)
        i.units = GlucoseUnit.MGDL
        i.value = 100.0
        Assertions.assertEquals(100.0, i.value, 0.01)
        Assertions.assertEquals(GlucoseUnit.MGDL, i.units)
    }
}