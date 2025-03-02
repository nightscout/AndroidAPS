package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

// only useful with active AutoISF

class InputIobTHTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputIobTH()
        i.value = 50
        assertThat(i.value).isEqualTo(50)
    }
}
