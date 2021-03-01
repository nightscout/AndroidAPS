package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class LabelWithElementTest : TriggerTestBase() {

    @Test
    fun constructorTest() {
        val l = LabelWithElement(injector, "A", "B", InputInsulin(injector))
        Assert.assertEquals("A", l.textPre)
        Assert.assertEquals("B", l.textPost)
        Assert.assertEquals(InputInsulin::class.java, l.element!!.javaClass)
    }
}