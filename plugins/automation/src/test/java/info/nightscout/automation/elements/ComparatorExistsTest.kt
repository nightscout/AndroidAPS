package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ComparatorExistsTest : TriggerTestBase() {

    @Test fun labelsTest() {
        Assertions.assertEquals(2, ComparatorExists.Compare.labels(rh).size)
    }

    @Test fun setValueTest() {
        val c = ComparatorExists(rh)
        c.value = ComparatorExists.Compare.NOT_EXISTS
        Assertions.assertEquals(ComparatorExists.Compare.NOT_EXISTS, c.value)
    }
}