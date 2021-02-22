package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class InputInsulinTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputInsulin(injector)
        i.value = 5.0
        Assert.assertEquals(5.0, i.value, 0.01)
    }
}