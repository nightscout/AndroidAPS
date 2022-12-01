package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class InputStringTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputString()
        i.value = "asd"
        Assert.assertEquals("asd", i.value)
    }
}