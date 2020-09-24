package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class InputStringTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputString(injector)
        i.value = "asd"
        Assert.assertEquals("asd", i.value)
    }
}