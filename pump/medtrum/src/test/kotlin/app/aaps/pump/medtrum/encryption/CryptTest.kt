package app.aaps.pump.medtrum.encryption

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CryptTest {

    @Test
    fun givenSNExpectKey() {
        val crypt = Crypt()

        val input = 2859923929
        val expected = 3364239851L
        val output: Long = crypt.keyGen(input)
        assertThat(output).isEqualTo(expected)
    }

    @Test
    fun givenSNExpectReal() {
        val crypt = Crypt()

        val input = 2859923929
        val expected = 126009121L
        val output: Long = crypt.simpleDecrypt(input)
        assertThat(output).isEqualTo(expected)
    }
}
