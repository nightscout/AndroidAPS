package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class InputDurationTest : TriggerTestBase() {

    @Test fun setValueTest() {
        var i = InputDuration(5, InputDuration.TimeUnit.MINUTES)
        assertThat(i.value).isEqualTo(5)
        assertThat(i.unit).isEqualTo(InputDuration.TimeUnit.MINUTES)
        i = InputDuration(5, InputDuration.TimeUnit.HOURS)
        assertThat(i.value).isEqualTo(5)
        assertThat(i.unit).isEqualTo(InputDuration.TimeUnit.HOURS)
        assertThat(i.getMinutes()).isEqualTo(5 * 60)
        i.setMinutes(60)
        assertThat(i.value).isEqualTo(1)
    }
}
