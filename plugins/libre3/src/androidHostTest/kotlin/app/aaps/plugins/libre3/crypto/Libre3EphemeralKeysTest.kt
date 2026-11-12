package app.aaps.plugins.libre3.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Key pair rules, and the guard that keeps a random key pair out of a first pairing. */
class Libre3EphemeralKeysTest {

    @Test
    fun `a reconnect key pair has a 65 byte point and pads to 72`() {
        val pair = Libre3EphemeralKeyPair.randomForReconnect()

        assertThat(pair.publicKey65).hasLength(65)
        assertThat(pair.publicKey65[0]).isEqualTo(0x04.toByte())
        assertThat(pair.publicKeyPadded72).hasLength(72)
        assertThat(pair.publicKeyPadded72.copyOfRange(65, 72)).isEqualTo(ByteArray(7))
    }

    @Test
    fun `two sides reach the same shared secret`() {
        val phone = Libre3EphemeralKeyPair.randomForReconnect()
        val sensor = Libre3EphemeralKeyPair.randomForReconnect()

        val fromPhone = phone.sharedSecret(sensor.publicKey65)
        val fromSensor = sensor.sharedSecret(phone.publicKey65)

        assertThat(fromPhone).isEqualTo(fromSensor)
    }

    @Test
    fun `a point that is not in the uncompressed form is refused`() {
        val phone = Libre3EphemeralKeyPair.randomForReconnect()

        assertThrows<Libre3CryptoException> { phone.sharedSecret(ByteArray(65)) }
        assertThrows<Libre3CryptoException> { phone.sharedSecret(ByteArray(64) { 0x04 }) }
    }

    @Test
    fun `a random key pair is never marked as first pairing material`() {
        assertThat(Libre3EphemeralKeyPair.randomForReconnect().isFirstPairMaterial).isFalse()
    }

    @Test
    fun `the first pairing scheme can run in this build`() {
        assertThat(Libre3FirstPairEphemeral.isAvailable).isTrue()
    }

    @Test
    fun `a first pairing key pair carries the scheme's own point, not the point of its scalar`() {
        // The captured entropy of a real Android trace, and the sixty five bytes that went out on
        // the wire for it. See Libre3FirstPairProcess2P5PublicTest for where they come from.
        val entropy = hexOf(
            "8987c91f1595e8a060e4cba652368ae8797e9113cfd412bebd0ea1a03783ae59" +
                "ee70d2c947578803b06b275c96632d148b81658bb87a3eabb5755273c40c397" +
                "f7255f3c1d742df608383fbbfff5a9b9fbc11a1ab525382024c85687cf79c2" +
                "a391ca7cc309ff82fe098c2d86e49f8b26364153f0bcb8945c887f5a2a7b5" +
                "4d568daa373a86c85c283fbb6285f35dca2d30263c34ce182c1fc63e6022a" +
                "3c7e6eaebe3a473d3c754bb8f3982172431af66388948aaf5c709f6699b76" +
                "08dcd161811dda99c61b302f46684433e61ef2afa4dd9f8b0f2472f612019" +
                "7cdfc0b940ad5f93ac01fc7497fb355c753df9c65fc68721690c35a09550fb" +
                "3c326e38bcbe37ebb309a680c383967627f58a108e1e94ecd16c5d2bc2f57" +
                "6dabdc7b"
        )
        val material = Libre3FirstPairEphemeral.make(FixedRandom(entropy))

        assertThat(material.attempts).isEqualTo(1)
        assertThat(material.entropy11A).isEqualTo(entropy)
        assertThat(material.scalarWindow).hasLength(70)
        assertThat(material.keyPair.isFirstPairMaterial).isTrue()
        assertThat(hex(material.keyPair.publicKey65)).isEqualTo(
            "04b60e0f455a1f2ebc3a1246d9311a66722f80fbc0cbdc23d18ae5e50693eed2" +
                "b1ea74d24eddcc8dd1957cf621a1f5514fcd7b40ec37f18f8c8060db6f8076b121"
        )
        assertThat(material.keyPair.publicKeyPadded72).hasLength(72)

        // The point on the wire is the scheme's own and does not belong to the private scalar, so
        // an ordinary key agreement on this key pair would hand back a number that looks fine and
        // means nothing. It has to be refused rather than answered.
        val peer = Libre3EphemeralKeyPair.randomForReconnect()
        assertThrows<Libre3CryptoException> { material.keyPair.sharedSecret(peer.publicKey65) }
    }

    @Test
    fun `a first pairing refuses a scalar window that is too short or a point that is not uncompressed`() {
        assertThrows<Libre3CryptoException> {
            Libre3EphemeralKeyPair.fromScalarWindow(ByteArray(31), ByteArray(65) { if (it == 0) 4 else 1 })
        }
        assertThrows<Libre3CryptoException> {
            Libre3EphemeralKeyPair.fromScalarWindow(ByteArray(70) { 1 }, ByteArray(65))
        }
    }

    /** A source of "random" bytes that always hands back the same draw, for a repeatable test. */
    private class FixedRandom(private val fixed: ByteArray) : Libre3RandomSource {

        override fun nextBytes(out: ByteArray) {
            require(out.size == fixed.size) { "this test source only makes ${fixed.size} bytes" }
            fixed.copyInto(out)
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun hexOf(text: String) = ByteArray(text.length / 2) {
        text.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}
