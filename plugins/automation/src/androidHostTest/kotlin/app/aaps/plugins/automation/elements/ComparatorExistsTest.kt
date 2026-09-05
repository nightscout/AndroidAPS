package app.aaps.plugins.automation.elements

import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ComparatorExistsTest : TriggerTestBase() {

    @Test fun labelsTest() {
        assertThat(ComparatorExists.Compare.labels(rh)).hasSize(2)
    }

    @Test fun setValueTest() {
        val c = ComparatorExists(rh)
        c.value = ComparatorExists.Compare.NOT_EXISTS
        assertThat(c.value).isEqualTo(ComparatorExists.Compare.NOT_EXISTS)
    }
}
