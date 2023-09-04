package info.nightscout.pump.medtrum.encryption

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CryptTest {

    @Test
    fun givenSNExpectKey() {
        val crypt = Crypt()

        val input = 2859923929
        val expect = 3364239851
        val output: Long = crypt.keyGen(input)
        Assertions.assertEquals(expect, output)
    }

    @Test
    fun givenSNExpectReal() {
        val crypt = Crypt()

        val input = 2859923929
        val expect: Long = 126009121
        val output: Long = crypt.simpleDecrypt(input)
        Assertions.assertEquals(expect, output)
    }
}
