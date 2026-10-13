package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class NonceTest : TestBase() {

    @Test
    fun checkDefaultNonceIncrement() {
        // Increment the nonce by the default amount of 1.

        val firstNonce = Nonce(listOf(0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        val secondNonce = firstNonce.getIncrementedNonce()
        val expectedSecondNonce = Nonce(listOf(0x11, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

        assertEquals(expectedSecondNonce, secondNonce)
    }

    @Test
    fun checkExplicitNonceIncrement() {
        // Increment the nonce by the explicit amount of 76000.

        val firstNonce = Nonce(listOf(0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        val secondNonce = firstNonce.getIncrementedNonce(incrementAmount = 76000)
        val expectedSecondNonce = Nonce(listOf(0xF0.toByte(), 0x29, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

        assertEquals(expectedSecondNonce, secondNonce)
    }

    @Test
    fun checkNonceWraparound() {
        // Increment a nonce that is high enough to cause a wrap-around.

        val firstNonce = Nonce(
            listOf(
                0xFA.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
            )
        )
        val secondNonce = firstNonce.getIncrementedNonce(incrementAmount = 10)
        val expectedSecondNonce = Nonce(listOf(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))

        assertEquals(expectedSecondNonce, secondNonce)
    }
}
