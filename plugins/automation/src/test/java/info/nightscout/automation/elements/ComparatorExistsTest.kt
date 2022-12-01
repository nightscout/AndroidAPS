package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

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