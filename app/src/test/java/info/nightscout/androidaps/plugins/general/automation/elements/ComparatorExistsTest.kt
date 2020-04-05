package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ComparatorExistsTest : TriggerTestBase() {

    @Test fun labelsTest() {
        Assert.assertEquals(2, ComparatorExists.Compare.labels(resourceHelper).size)
    }

    @Test fun setValueTest() {
        val c = ComparatorExists(injector)
        c.value = ComparatorExists.Compare.NOT_EXISTS
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, c.value)
    }
}