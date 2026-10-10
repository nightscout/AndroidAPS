package app.aaps.pump.eopatch.core.exception

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PatchExceptionsTest {

    @Test
    fun `NoActivatedPatchException should be an Exception`() {
        val ex = NoActivatedPatchException()
        assertThat(ex).isInstanceOf(Exception::class.java)
    }

    @Test
    fun `PatchDisconnectedException should be an Exception`() {
        val ex = PatchDisconnectedException()
        assertThat(ex).isInstanceOf(Exception::class.java)
    }
}
