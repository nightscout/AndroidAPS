package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ComparatorConnectTest : TriggerTestBase() {

    @Test fun labelsTest() {
        assertThat(ComparatorConnect.Compare.labels(text)).hasSize(2)
    }

    @Test fun setValueTest() {
        val c = ComparatorConnect(text)
        c.value = ComparatorConnect.Compare.ON_DISCONNECT
        assertThat(c.value).isEqualTo(ComparatorConnect.Compare.ON_DISCONNECT)
    }
}
