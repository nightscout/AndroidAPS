package app.aaps.pump.eopatch

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FloatFormattersTest {

    @Test
    fun `insulin formatter should format with correct precision`() {
        val result = FloatFormatters.insulin(5.25f)

        assertThat(result).isNotNull()
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `insulin formatter with unit should include unit`() {
        val result = FloatFormatters.insulin(5.25f, "U")

        assertThat(result).contains("U")
    }

    @Test
    fun `insulin formatter should handle zero`() {
        val result = FloatFormatters.insulin(0f)

        assertThat(result).isNotNull()
        assertThat(result).contains("0")
    }

    @Test
    fun `insulin formatter should handle small values`() {
        val result = FloatFormatters.insulin(0.05f)

        assertThat(result).isNotNull()
    }

    @Test
    fun `insulin formatter should handle large values`() {
        val result = FloatFormatters.insulin(250.5f)

        assertThat(result).isNotNull()
    }

    @Test
    fun `insulin formatter should handle negative values`() {
        val result = FloatFormatters.insulin(-5.0f)

        assertThat(result).isNotNull()
    }
}
