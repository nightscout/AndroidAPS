package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class InputInsulinTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputInsulin()
        i.value = 5.0
        Assert.assertEquals(5.0, i.value, 0.01)
    }
}