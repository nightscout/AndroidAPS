package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class LabelWithElementTest : TriggerTestBase() {

    @Test
    fun constructorTest() {
        val l = LabelWithElement(rh, "A", "B", InputInsulin())
        Assertions.assertEquals("A", l.textPre)
        Assertions.assertEquals("B", l.textPost)
        Assertions.assertEquals(InputInsulin::class.java, l.element!!.javaClass)
    }
}