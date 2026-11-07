package app.aaps.plugins.automation.elements

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class InputTempTargetTest : TriggerTestBase() {

    @Test fun setValueTest() {
        val i = InputTempTarget(profileFunction)
        i.units = GlucoseUnit.MMOL
        i.value = 5.0
        assertThat(i.value).isWithin(0.01).of(5.0)
        i.units = GlucoseUnit.MGDL
        i.value = 100.0
        assertThat(i.value).isWithin(0.01).of(100.0)
        assertThat(i.units).isEqualTo(GlucoseUnit.MGDL)
    }
}
