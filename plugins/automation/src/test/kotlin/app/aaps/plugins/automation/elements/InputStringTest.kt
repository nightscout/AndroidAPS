package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputStringTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputString()
        i.value = "asd"
        Assertions.assertEquals("asd", i.value)
    }
}