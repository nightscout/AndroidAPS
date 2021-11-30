package info.nightscout.androidaps.plugins.general.automation.elements

import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.Test

class LabelWithElementTest : TriggerTestBase() {

    @Test
    fun constructorTest() {
        val l = LabelWithElement(rh, "A", "B", InputInsulin())
        Assert.assertEquals("A", l.textPre)
        Assert.assertEquals("B", l.textPost)
        Assert.assertEquals(InputInsulin::class.java, l.element!!.javaClass)
    }
}