package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class InputCarePortalEventTest : TriggerTestBase() {

    @Test
    fun labelsTest() {
        Assert.assertEquals(4, InputCarePortalMenu.EventType.labels(rh).size)
    }

    @Test
    fun setValueTest() {
        val cp = InputCarePortalMenu(rh, InputCarePortalMenu.EventType.EXERCISE)
        Assert.assertEquals(InputCarePortalMenu.EventType.EXERCISE, cp.value)
    }
}