package app.aaps.pump.eopatch.core.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BolusTypeTest {

    @Test
    fun `enum values should have correct indices`() {
        assertThat(BolusType.NOW.index).isEqualTo(0)
        assertThat(BolusType.EXT.index).isEqualTo(1)
        assertThat(BolusType.COMBO.index).isEqualTo(2)
    }

    @Test
    fun `should have exactly 3 values`() {
        assertThat(BolusType.entries).hasSize(3)
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertThat(BolusType.valueOf("NOW")).isEqualTo(BolusType.NOW)
        assertThat(BolusType.valueOf("EXT")).isEqualTo(BolusType.EXT)
        assertThat(BolusType.valueOf("COMBO")).isEqualTo(BolusType.COMBO)
    }
}
