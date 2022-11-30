package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerDummy
import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class StaticLabelTest : TriggerTestBase() {

    @Test fun constructor() {
        var sl = StaticLabel(rh, "any", TriggerDummy(injector))
        Assert.assertEquals("any", sl.label)
        `when`(rh.gs(info.nightscout.core.main.R.string.pumplimit)).thenReturn("pump limit")
        sl = StaticLabel(rh, info.nightscout.core.main.R.string.pumplimit, TriggerDummy(injector))
        Assert.assertEquals("pump limit", sl.label)
    }
}