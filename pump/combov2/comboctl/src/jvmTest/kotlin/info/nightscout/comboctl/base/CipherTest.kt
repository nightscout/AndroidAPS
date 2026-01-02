package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class CipherTest : TestBase() {

    @Test
    fun checkWeakKeyGeneration() {
        // Generate a weak key out of the PIN 012-345-6789.

        val PIN = PairingPIN(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))

        try {
            val expectedWeakKey = byteArrayListOfInts(
                0x30, 0x31, 0x32, 0x33,
                0x34, 0x35, 0x36, 0x37,
                0x38, 0x39, 0xcf, 0xce,
                0xcd, 0xcc, 0xcb, 0xca
            )
            val actualWeakKey = generateWeakKeyFromPIN(PIN)
            assertEquals(expectedWeakKey, actualWeakKey.toList())
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw Error("Unexpected exception: $ex")
        }
    }

    @Test
    fun checkEncryptDecrypt() {
        // Encrypt and decrypt the text "0123456789abcdef".
        // Verify that the encrypted version is what we expect,
        // and that decrypting that version yields the original text.
        // For this test, we use a key that is simply the value 48
        // (= ASCII index of the character '0'), repeated 16 times.
        // (16 is the number of bytes in a 128-bit key.)

        val inputData = "0123456789abcdef".toByteArray(Charsets.UTF_8)

        val key = ByteArray(CIPHER_KEY_SIZE)
        key.fill('0'.code.toByte())

        val cipher = Cipher(key)

        val expectedEncryptedData = byteArrayListOfInts(
            0xb3, 0x58, 0x09, 0xd0,
            0xe3, 0xb4, 0xa0, 0x2e,
            0x1a, 0xbb, 0x6b, 0x1a,
            0xfa, 0xeb, 0x31, 0xc8
        )
        val actualEncryptedData = cipher.encrypt(inputData)
        assertEquals(expectedEncryptedData, actualEncryptedData.toList())

        val decryptedData = cipher.decrypt(actualEncryptedData)
        assertEquals(inputData.toList(), decryptedData.toList())
    }
}
