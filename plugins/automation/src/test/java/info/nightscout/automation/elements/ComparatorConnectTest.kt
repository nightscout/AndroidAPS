package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ComparatorConnectTest : TriggerTestBase() {

    @Test fun labelsTest() {
        Assertions.assertEquals(2, ComparatorConnect.Compare.labels(rh).size)
    }

    @Test fun setValueTest() {
        val c = ComparatorConnect(rh)
        c.value = ComparatorConnect.Compare.ON_DISCONNECT
        Assertions.assertEquals(ComparatorConnect.Compare.ON_DISCONNECT, c.value)
    }
}