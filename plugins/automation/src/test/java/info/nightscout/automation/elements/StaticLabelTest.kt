package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerDummy
import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class StaticLabelTest : TriggerTestBase() {

    @Test fun constructor() {
        var sl = StaticLabel(rh, "any", TriggerDummy(injector))
        Assertions.assertEquals("any", sl.label)
        `when`(rh.gs(info.nightscout.core.ui.R.string.pumplimit)).thenReturn("pump limit")
        sl = StaticLabel(rh, info.nightscout.core.ui.R.string.pumplimit, TriggerDummy(injector))
        Assertions.assertEquals("pump limit", sl.label)
    }
}