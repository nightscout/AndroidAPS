package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.`when`

class StaticLabelTest : TriggerTestBase() {

    @Test fun constructor() {
        var sl = StaticLabel(rh, "any", TriggerDummy(injector))
        Assert.assertEquals("any", sl.label)
        `when`(rh.gs(R.string.pumplimit)).thenReturn("pump limit")
        sl = StaticLabel(rh, R.string.pumplimit, TriggerDummy(injector))
        Assert.assertEquals("pump limit", sl.label)
    }
}