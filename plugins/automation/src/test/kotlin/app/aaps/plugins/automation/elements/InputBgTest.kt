package app.aaps.plugins.automation.elements

import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.plugins.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class InputBgTest : TriggerTestBase() {

    @Test
    fun setValueTest() {
        var i: InputBg = InputBg(profileFunction).setUnits(GlucoseUnit.MMOL).setValue(5.0)
        Assertions.assertEquals(5.0, i.value, 0.01)
        Assertions.assertEquals(InputBg.MMOL_MIN, i.minValue, 0.01)
        i = InputBg(profileFunction).setValue(100.0).setUnits(GlucoseUnit.MGDL)
        Assertions.assertEquals(100.0, i.value, 0.01)
        Assertions.assertEquals(InputBg.MGDL_MIN, i.minValue, 0.01)
        Assertions.assertEquals(GlucoseUnit.MGDL, i.units)
    }

    @BeforeEach
    fun prepare() {
        `when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }
}