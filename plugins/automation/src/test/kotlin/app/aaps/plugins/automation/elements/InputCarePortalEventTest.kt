package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputCarePortalEventTest : TriggerTestBase() {

    @Test
    fun labelsTest() {
        Assertions.assertEquals(4, InputCarePortalMenu.EventType.labels(rh).size)
    }

    @Test
    fun setValueTest() {
        val cp = InputCarePortalMenu(rh, InputCarePortalMenu.EventType.EXERCISE)
        Assertions.assertEquals(InputCarePortalMenu.EventType.EXERCISE, cp.value)
    }
}