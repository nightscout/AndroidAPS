package app.aaps.plugins.automation.elements

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.automation.triggers.TriggerTestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class InputBgTest : TriggerTestBase() {

    @Test
    fun setValueTest() {
        var i: InputBg = InputBg(profileFunction).setUnits(GlucoseUnit.MMOL).setValue(5.0)
        assertThat(i.value).isWithin(0.01).of(5.0)
        assertThat(i.minValue).isWithin(0.01).of(InputBg.MMOL_MIN)
        i = InputBg(profileFunction).setValue(100.0).setUnits(GlucoseUnit.MGDL)
        assertThat(i.value).isWithin(0.01).of(100.0)
        assertThat(i.minValue).isWithin(0.01).of(InputBg.MGDL_MIN)
        assertThat(i.units).isEqualTo(GlucoseUnit.MGDL)
    }

    @BeforeEach
    fun prepare() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }
}
