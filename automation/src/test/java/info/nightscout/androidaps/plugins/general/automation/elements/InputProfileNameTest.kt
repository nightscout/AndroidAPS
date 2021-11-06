package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test

class InputProfileNameTest : TriggerTestBase() {

    @Test fun setValue() {
        val inputProfileName = InputProfileName(rh, activePlugin, "Test")
        Assert.assertEquals("Test", inputProfileName.value)
        inputProfileName.value = "Test2"
        Assert.assertEquals("Test2", inputProfileName.value)
    }
}