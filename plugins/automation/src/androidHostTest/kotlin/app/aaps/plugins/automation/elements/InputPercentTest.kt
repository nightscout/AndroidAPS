package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class InputPercentTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputPercent()
        i.value = 10.0
        assertThat(i.value).isWithin(0.01).of(10.0)
    }
}
