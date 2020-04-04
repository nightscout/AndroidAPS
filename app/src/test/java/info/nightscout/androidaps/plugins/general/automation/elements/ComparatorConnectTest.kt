package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ComparatorConnectTest : TriggerTestBase() {

    @Test fun labelsTest() {
        Assert.assertEquals(2, ComparatorConnect.Compare.labels(resourceHelper).size)
    }

    @Test fun setValueTest() {
        val c = ComparatorConnect(injector)
        c.value = ComparatorConnect.Compare.ON_DISCONNECT
        Assert.assertEquals(ComparatorConnect.Compare.ON_DISCONNECT, c.value)
    }
}