package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class InputPercentTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputPercent()
        i.value = 10.0
        Assert.assertEquals(10.0, i.value, 0.01)
    }
}