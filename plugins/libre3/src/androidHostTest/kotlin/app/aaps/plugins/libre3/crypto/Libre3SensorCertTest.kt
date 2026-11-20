package app.aaps.plugins.libre3.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * The sensor certificate: its shape, and the signature check that lets a pairing go on.
 *
 * The two real signing keys of the sensor maker are public, but their private halves are not, so
 * a test cannot make a certificate they would accept. These tests use a key pair of their own
 * instead, which is exactly why [Libre3SensorCert.isSignedByKnownKey] takes the list of keys.
 */
class Libre3SensorCertTest {

    private fun p256KeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    private fun point65(pair: KeyPair): ByteArray {
        val point = (pair.public as ECPublicKey).w
        fun fixed(value: java.math.BigInteger): ByteArray {
            val raw = value.toByteArray()
            val trimmed = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
            return ByteArray(32 - trimmed.size) + trimmed
        }
        return byteArrayOf(0x04) + fixed(point.affineX) + fixed(point.affineY)
    }

    /** Signs the 76 byte payload and writes the answer back as the plain 64 byte r then s. */
    private fun certSignedBy(pair: KeyPair, point: ByteArray): ByteArray {
        val raw = ByteArray(Libre3SensorCert.TOTAL_SIZE) { (it and 0xFF).toByte() }
        point.copyInto(raw, 11)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(pair.private)
        signer.update(raw.copyOfRange(0, 76))
        val der = signer.sign()
        rawFromDer(der).copyInto(raw, 76)
        return raw
    }

    /** Turns the tagged form the phone makes back into the plain 64 bytes the wire carries. */
    private fun rawFromDer(der: ByteArray): ByteArray {
        var index = 2
        fun readInteger(): ByteArray {
            index += 1
            val length = der[index].toInt() and 0xFF
            index += 1
            val value = der.copyOfRange(index, index + length)
            index += length
            val trimmed = if (value.size > 32) value.copyOfRange(value.size - 32, value.size) else value
            return ByteArray(32 - trimmed.size) + trimmed
        }
        return readInteger() + readInteger()
    }

    @Test
    fun `the point and the signature are read from the published offsets`() {
        val raw = ByteArray(Libre3SensorCert.TOTAL_SIZE) { (it and 0xFF).toByte() }
        raw[11] = 0x04
        val cert = Libre3SensorCert.parse(raw)

        assertThat(cert.staticPublicKey).hasLength(65)
        assertThat(cert.staticPublicKey[0]).isEqualTo(0x04.toByte())
        assertThat(cert.staticPublicKey).isEqualTo(raw.copyOfRange(11, 76))
        assertThat(cert.signedPayload).isEqualTo(raw.copyOfRange(0, 76))
        assertThat(cert.signature).hasLength(64)
        assertThat(cert.signature).isEqualTo(raw.copyOfRange(76, 140))
    }

    @Test
    fun `a certificate of the wrong size or without an uncompressed point is refused`() {
        assertThrows<Libre3CryptoException> { Libre3SensorCert.parse(ByteArray(139)) }
        assertThrows<Libre3CryptoException> { Libre3SensorCert.parse(ByteArray(141)) }
        assertThrows<Libre3CryptoException> { Libre3SensorCert.parse(ByteArray(140)) }
    }

    @Test
    fun `a certificate is accepted only by the key that really signed it`() {
        val signer = p256KeyPair()
        val stranger = p256KeyPair()
        val sensorPoint = point65(p256KeyPair())
        val cert = Libre3SensorCert.parse(certSignedBy(signer, sensorPoint))

        assertThat(cert.isSignedByKnownKey(listOf(point65(signer)))).isTrue()
        assertThat(cert.isSignedByKnownKey(listOf(point65(stranger)))).isFalse()
        // The right key among several is enough, which is how the two families of the real driver
        // are handled.
        assertThat(cert.isSignedByKnownKey(listOf(point65(stranger), point65(signer)))).isTrue()
    }

    @Test
    fun `a certificate whose payload was changed no longer verifies`() {
        val signer = p256KeyPair()
        val raw = certSignedBy(signer, point65(p256KeyPair()))
        assertThat(Libre3SensorCert.parse(raw).isSignedByKnownKey(listOf(point65(signer)))).isTrue()

        // One bit of the signed part is enough. Byte 12 is inside the sensor's own point.
        raw[12] = (raw[12].toInt() xor 1).toByte()
        assertThat(Libre3SensorCert.parse(raw).isSignedByKnownKey(listOf(point65(signer)))).isFalse()
    }

    @Test
    fun `an unsigned certificate is refused by the real keys`() {
        val raw = ByteArray(Libre3SensorCert.TOTAL_SIZE) { (it and 0xFF).toByte() }
        raw[11] = 0x04
        assertThat(Libre3SensorCert.parse(raw).isSignedByKnownKey()).isFalse()
    }

    @Test
    fun `the two signing keys of the sensor maker ship with the driver`() {
        assertThat(Libre3SensorCert.KNOWN_SIGNING_KEYS).hasSize(2)
        for (key in Libre3SensorCert.KNOWN_SIGNING_KEYS) {
            assertThat(key).hasLength(65)
            assertThat(key[0]).isEqualTo(0x04.toByte())
        }
    }
}
