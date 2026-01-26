package app.aaps.core.interfaces.pump

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test

class PumpInsulinTest : TestBase() {

    @Test
    fun testConversion() {
        assertThat(PumpInsulin(1.0).iU(5.0)).isEqualTo(5.0)
        assertThat(PumpInsulin(1.0).iU(0.5)).isEqualTo(0.5)
    }
}