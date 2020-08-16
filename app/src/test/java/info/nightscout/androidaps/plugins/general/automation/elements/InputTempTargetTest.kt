package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class InputTempTargetTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputTempTarget(injector)
        i.units = Constants.MMOL
        i.value = 5.0
        Assert.assertEquals(5.0, i.value, 0.01)
        i.units = Constants.MGDL
        i.value = 100.0
        Assert.assertEquals(100.0, i.value, 0.01)
        Assert.assertEquals(Constants.MGDL, i.units)
    }
}