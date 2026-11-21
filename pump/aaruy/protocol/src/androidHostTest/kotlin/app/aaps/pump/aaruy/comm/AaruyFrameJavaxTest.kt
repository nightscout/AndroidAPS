package app.aaps.pump.aaruy.comm

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * The frames were encrypted with `javax.crypto` `AES/ECB/PKCS5Padding`, as the vendor library did and as every real pump
 * test ran. The shared AES must give the same bytes both ways, for every length of padding.
 */
class AaruyFrameJavaxTest {

    private fun javax(mode: Int): Cipher = Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(aaruyFrameKey(), "AES")) }

    @Test
    fun aFrameHasTheBytesOfTheVendorCipher() {
        for (size in 0..40) {
            val payload = ByteArray(size) { (it * 7 + size).toByte() }
            val frame = AaruyFrame.buildWrite(14, 3, payload)
            val body = frame.copyOfRange(3, frame.size)
            val plain = javax(Cipher.DECRYPT_MODE).doFinal(body)
            assertContentEquals(body, javax(Cipher.ENCRYPT_MODE).doFinal(plain), "payload of $size bytes")
        }
    }

    @Test
    fun aFrameOfTheVendorCipherIsRead() {
        val plain = byteArrayOf(7, 0, 1, 15, 0, 9, 0, 80, 2)
        val encrypted = javax(Cipher.ENCRYPT_MODE).doFinal(plain)
        val frame = byteArrayOf(0x55, encrypted.size.toByte(), 0) + encrypted

        val message = AaruyFrame.parse(frame)!!
        assertEquals(15.toShort(), message.paramId)
        assertContentEquals(byteArrayOf(80, 2), message.payload)
    }
}
