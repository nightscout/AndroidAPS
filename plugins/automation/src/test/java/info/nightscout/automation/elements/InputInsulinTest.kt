package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputInsulinTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputInsulin()
        i.value = 5.0
        Assertions.assertEquals(5.0, i.value, 0.01)
    }
}