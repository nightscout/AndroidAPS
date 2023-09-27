package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputPercentTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputPercent()
        i.value = 10.0
        Assertions.assertEquals(10.0, i.value, 0.01)
    }
}