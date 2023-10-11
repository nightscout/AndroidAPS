package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class InputCarePortalEventTest : TriggerTestBase() {

    @Test
    fun labelsTest() {
        assertThat(InputCarePortalMenu.EventType.labels(rh)).hasSize(4)
    }

    @Test
    fun setValueTest() {
        val cp = InputCarePortalMenu(rh, InputCarePortalMenu.EventType.EXERCISE)
        assertThat(cp.value).isEqualTo(InputCarePortalMenu.EventType.EXERCISE)
    }
}
