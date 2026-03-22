package app.aaps.pump.eopatch.core.code

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchAeCodeTest {

    @Test
    fun `create should set aeValue`() {
        val code = PatchAeCode.create(42, 100)
        assertThat(code.aeValue).isEqualTo(42)
    }

    @Test
    fun `create should set values correctly for zero`() {
        val code = PatchAeCode.create(0, 0)
        assertThat(code.aeValue).isEqualTo(0)
    }

    @Test
    fun `different instances should be independent`() {
        val code1 = PatchAeCode.create(1, 10)
        val code2 = PatchAeCode.create(2, 20)
        assertThat(code1.aeValue).isNotEqualTo(code2.aeValue)
    }
}
