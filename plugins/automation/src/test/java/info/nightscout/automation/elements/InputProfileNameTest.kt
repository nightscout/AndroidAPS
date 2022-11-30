package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class InputProfileNameTest : TriggerTestBase() {

    @Test fun setValue() {
        val inputProfileName = InputProfileName(rh, activePlugin, "Test")
        Assert.assertEquals("Test", inputProfileName.value)
        inputProfileName.value = "Test2"
        Assert.assertEquals("Test2", inputProfileName.value)
    }
}