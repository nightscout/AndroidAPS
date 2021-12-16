package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test

class ComparatorExistsTest : TriggerTestBase() {

    @Test fun labelsTest() {
        Assert.assertEquals(2, ComparatorExists.Compare.labels(rh).size)
    }

    @Test fun setValueTest() {
        val c = ComparatorExists(rh)
        c.value = ComparatorExists.Compare.NOT_EXISTS
        Assert.assertEquals(ComparatorExists.Compare.NOT_EXISTS, c.value)
    }
}