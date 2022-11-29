package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import info.nightscout.interfaces.GlucoseUnit
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class InputBgTest : TriggerTestBase() {

    @Test
    fun setValueTest() {
        var i: InputBg = InputBg(profileFunction).setUnits(GlucoseUnit.MMOL).setValue(5.0)
        Assert.assertEquals(5.0, i.value, 0.01)
        Assert.assertEquals(InputBg.MMOL_MIN, i.minValue, 0.01)
        i = InputBg(profileFunction).setValue(100.0).setUnits(GlucoseUnit.MGDL)
        Assert.assertEquals(100.0, i.value, 0.01)
        Assert.assertEquals(InputBg.MGDL_MIN, i.minValue, 0.01)
        Assert.assertEquals(GlucoseUnit.MGDL, i.units)
    }

    @BeforeEach
    fun prepare() {
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }
}