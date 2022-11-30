package info.nightscout.automation.elements

import info.nightscout.automation.triggers.TriggerTestBase
import org.junit.Assert
import org.junit.jupiter.api.Test

class LabelWithElementTest : TriggerTestBase() {

    @Test
    fun constructorTest() {
        val l = LabelWithElement(rh, "A", "B", InputInsulin())
        Assert.assertEquals("A", l.textPre)
        Assert.assertEquals("B", l.textPost)
        Assert.assertEquals(InputInsulin::class.java, l.element!!.javaClass)
    }
}