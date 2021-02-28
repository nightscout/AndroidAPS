package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class InputPercentTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputPercent(injector)
        i.value = 10.0
        Assert.assertEquals(10.0, i.value, 0.01)
    }
}