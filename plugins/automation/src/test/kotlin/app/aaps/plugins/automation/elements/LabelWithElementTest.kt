package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class LabelWithElementTest : TriggerTestBase() {

    @Test
    fun constructorTest() {
        val l = LabelWithElement(rh, "A", "B", InputInsulin())
        assertThat(l.textPre).isEqualTo("A")
        assertThat(l.textPost).isEqualTo("B")
        assertIs<InputInsulin>(l.element)
    }
}
