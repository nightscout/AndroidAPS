package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerDummy
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class StaticLabelTest : TriggerTestBase() {

    @Test fun constructor() {
        var sl = StaticLabel(resourceHelper, "any", TriggerDummy(injector))
        Assert.assertEquals("any", sl.label)
        `when`(resourceHelper.gs(R.string.pumplimit)).thenReturn("pump limit")
        sl = StaticLabel(resourceHelper, R.string.pumplimit, TriggerDummy(injector))
        Assert.assertEquals("pump limit", sl.label)
    }
}