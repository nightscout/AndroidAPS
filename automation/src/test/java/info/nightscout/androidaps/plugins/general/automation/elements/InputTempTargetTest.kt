package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.interfaces.GlucoseUnit
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test

class InputTempTargetTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputTempTarget(profileFunction)
        i.units = GlucoseUnit.MMOL
        i.value = 5.0
        Assert.assertEquals(5.0, i.value, 0.01)
        i.units = GlucoseUnit.MGDL
        i.value = 100.0
        Assert.assertEquals(100.0, i.value, 0.01)
        Assert.assertEquals(GlucoseUnit.MGDL, i.units)
    }
}