package app.aaps.plugins.libre3.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Shapes and checks of the two pairing messages.
 *
 * The block maker used here is the ordinary AES of the phone, because these tests only prove the
 * shapes and the echo checks. On a real sensor the pairing messages use the sensor's own block
 * maker, which `Libre3LibAesTest` checks on its own.
 */
class Libre3PairingMessagesTest {

    private val blockMaker = Libre3AesCcm.standardAes(ByteArray(16) { it.toByte() })
    private val phoneR2 = ByteArray(16) { (it + 0x40).toByte() }
    private val sensorR1 = ByteArray(16) { (it + 0x80).toByte() }
    private val blePin = byteArrayOf(0x32, 0x25, 0xEC.toByte(), 0x72)
    private val nonce = ByteArray(7) { (it + 0x10).toByte() }

    @Test
    fun `the Phase 5 plain text is the sensor part, the phone part and the PIN`() {
        val plaintext = Libre3Phase5Challenge.plaintext(sensorR1, phoneR2, blePin)

        assertThat(plaintext).hasLength(36)
        assertThat(plaintext.copyOfRange(32, 36)).isEqualTo(blePin)
    }

    @Test
    fun `the Phase 5 message is 36 plus 4 bytes, padded to 54 on the wire`() {
        val plaintext = Libre3Phase5Challenge.plaintext(sensorR1, phoneR2, blePin)

        val message = Libre3Phase5Challenge.encrypt(plaintext, nonce, blockMaker)

        assertThat(message.ciphertext).hasLength(36)
        assertThat(message.tag).hasLength(4)
        assertThat(message.logicalBytes).hasLength(40)
        assertThat(message.wireBytes).hasLength(54)
        assertThat(message.wireBytes.copyOfRange(40, 54)).isEqualTo(ByteArray(14))
    }

    @Test
    fun `a Phase 5 message survives being written and read again`() {
        val plaintext = Libre3Phase5Challenge.plaintext(sensorR1, phoneR2, blePin)
        val message = Libre3Phase5Challenge.encrypt(plaintext, nonce, blockMaker)

        assertThat(Libre3Phase5Challenge.decode(message.wireBytes)).isEqualTo(message)
        assertThat(Libre3Phase5Challenge.decode(message.logicalBytes)).isEqualTo(message)
    }

    @Test
    fun `a Phase 5 message of the wrong length is refused`() {
        assertThrows<Libre3CryptoException> { Libre3Phase5Challenge.decode(ByteArray(53)) }
    }

    @Test
    fun `Phase 6 gives back the session keys when both echoes match`() {
        val response = buildPhase6(phoneR2, sensorR1)

        val material = response.decrypt(blockMaker, expectedPhoneR2 = phoneR2, expectedSensorR1 = sensorR1)

        assertThat(material.kEnc).hasLength(16)
        assertThat(material.ivEnc).hasLength(8)
    }

    @Test
    fun `Phase 6 is refused when the sensor does not echo what this phone sent`() {
        val response = buildPhase6(ByteArray(16) { 0x55 }, sensorR1)

        assertThrows<Libre3CryptoException> {
            response.decrypt(blockMaker, expectedPhoneR2 = phoneR2, expectedSensorR1 = sensorR1)
        }
    }

    @Test
    fun `Phase 6 is refused when the sensor does not echo its own random part`() {
        val response = buildPhase6(phoneR2, ByteArray(16) { 0x66 })

        assertThrows<Libre3CryptoException> {
            response.decrypt(blockMaker, expectedPhoneR2 = phoneR2, expectedSensorR1 = sensorR1)
        }
    }

    @Test
    fun `a Phase 6 message is 67 bytes on the wire`() {
        val response = buildPhase6(phoneR2, sensorR1)
        val wire = response.ciphertext + response.tag + response.nonce

        assertThat(wire).hasLength(67)
        assertThat(Libre3Phase6Response.decode(wire)).isEqualTo(response)
        assertThrows<Libre3CryptoException> { Libre3Phase6Response.decode(ByteArray(66)) }
    }

    /** Builds what a sensor would send, so the reading side can be checked on its own. */
    private fun buildPhase6(echoedR2: ByteArray, echoedR1: ByteArray): Libre3Phase6Response {
        val kEnc = ByteArray(16) { (it + 0x20).toByte() }
        val ivEnc = ByteArray(8) { (it + 0x30).toByte() }
        val plaintext = echoedR2 + echoedR1 + kEnc + ivEnc
        val sealed = Libre3AesCcm.encrypt(nonce, plaintext, tagLength = 4, aes = blockMaker)
        return Libre3Phase6Response(sealed.ciphertext, sealed.tag, nonce)
    }
}
