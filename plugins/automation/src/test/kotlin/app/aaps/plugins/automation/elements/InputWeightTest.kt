package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

// only useful with active AutoISF

class InputWeightTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputWeight()
        i.value = 0.5
        assertThat(i.value).isWithin(0.001).of(0.5)
    }
}
