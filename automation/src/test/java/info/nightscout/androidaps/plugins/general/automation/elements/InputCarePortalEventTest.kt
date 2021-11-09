package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test

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