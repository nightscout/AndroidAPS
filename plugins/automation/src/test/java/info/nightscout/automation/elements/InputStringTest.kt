package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputStringTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputString()
        i.value = "asd"
        Assertions.assertEquals("asd", i.value)
    }
}