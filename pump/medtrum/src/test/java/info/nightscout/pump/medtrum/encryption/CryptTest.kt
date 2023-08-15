package info.nightscout.pump.medtrum.encryption

import org.junit.jupiter.api.Test
import org.junit.Assert.*

class CryptTest {

    @Test
    fun givenSNExpectKey() {
        val crypt = Crypt()

        val input: Long  = 2859923929
        val expect: Long  = 3364239851
        val output: Long  = crypt.keyGen(input)
        assertEquals(expect, output)
    }

    @Test
    fun givenSNExpectReal() {
        val crypt = Crypt()

        val input: Long = 2859923929
        val expect: Long  = 126009121
        val output: Long  = crypt.simpleDecrypt(input)
        assertEquals(expect, output)
    }
}
