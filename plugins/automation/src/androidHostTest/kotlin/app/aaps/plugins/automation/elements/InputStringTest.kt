package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class InputStringTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputString()
        i.value = "asd"
        assertThat(i.value).isEqualTo("asd")
    }
}
