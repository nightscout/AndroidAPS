package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.plugins.general.automation.AutomationPlugin
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class InputBgTest : TriggerTestBase() {

    @Test
    fun setValueTest() {
        var i: InputBg = InputBg(injector).setUnits(Constants.MMOL).setValue(5.0)
        Assert.assertEquals(5.0, i.value, 0.01)
        Assert.assertEquals(InputBg.MMOL_MIN, i.minValue, 0.01)
        i = InputBg(injector).setValue(100.0).setUnits(Constants.MGDL)
        Assert.assertEquals(100.0, i.value, 0.01)
        Assert.assertEquals(InputBg.MGDL_MIN, i.minValue, 0.01)
        Assert.assertEquals(Constants.MGDL, i.units)
    }

    @Before
    fun prepare() {
        `when`(profileFunction.getUnits()).thenReturn(Constants.MGDL)
    }
}