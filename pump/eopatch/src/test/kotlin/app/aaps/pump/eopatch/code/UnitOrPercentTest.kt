package app.aaps.pump.eopatch.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class UnitOrPercentTest {

    @Test
    fun `should have two values`() {
        assertThat(UnitOrPercent.entries).hasSize(2)
        assertThat(UnitOrPercent.entries).containsExactly(UnitOrPercent.U, UnitOrPercent.P)
    }

    @Test
    fun `U should represent units`() {
        assertThat(UnitOrPercent.U).isNotNull()
    }

    @Test
    fun `P should represent percent`() {
        assertThat(UnitOrPercent.P).isNotNull()
    }

    @Test
    fun `values should be distinct`() {
        assertThat(UnitOrPercent.U).isNotEqualTo(UnitOrPercent.P)
    }

    @Test
    fun `should support valueOf`() {
        assertThat(UnitOrPercent.valueOf("U")).isEqualTo(UnitOrPercent.U)
        assertThat(UnitOrPercent.valueOf("P")).isEqualTo(UnitOrPercent.P)
    }
}
