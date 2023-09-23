package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputProfileNameTest : TriggerTestBase() {

    @Test fun setValue() {
        val inputProfileName = InputProfileName(rh, activePlugin, "Test")
        Assertions.assertEquals("Test", inputProfileName.value)
        inputProfileName.value = "Test2"
        Assertions.assertEquals("Test2", inputProfileName.value)
    }
}