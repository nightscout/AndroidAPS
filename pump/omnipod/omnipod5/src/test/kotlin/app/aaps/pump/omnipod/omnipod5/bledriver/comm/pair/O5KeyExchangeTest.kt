package app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.pump.omnipod.common.bledriver.pod.util.RandomByteGenerator
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * [O5KeyExchange] - real P-256/SHA-256 crypto is exercised via real generators (not hand-
 * verified against an external test vector, unlike [O5CertificateStoreTest]'s DER round-trip
 * or [PulseLogEntryTest]'s openomni golden value - no equivalent published vector was found
 * for this KDF). Tests instead check structural properties: sizes, byte-layout composition,
 * and round-trip behavior (e.g. increment-then-check-against-original), which don't depend on
 * knowing the "correct" hash output in advance.
 */
class O5KeyExchangeTest {

    private val aapsLogger = AAPSLoggerTest()
    private val controllerIdData = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    private fun newExchange(
        keyGenerator: P256KeyGenerator = P256KeyGenerator(),
        randomByteGenerator: RandomByteGenerator = RandomByteGenerator()
    ) = O5KeyExchange(aapsLogger, keyGenerator, randomByteGenerator, controllerIdData)

    /** ECDH requires a genuine point on the curve - a real generated public key, standing in
     *  for the pod's, unlike an arbitrary byte array which fails curve validation. */
    private fun realPublicKey(): ByteArray {
        val kg = P256KeyGenerator()
        return kg.publicFromPrivate(kg.generatePrivateKey())
    }

    @Test
    fun `constructor generates a correctly-sized nonce and public key`() {
        val ke = newExchange()

        assertThat(ke.pdmNonce).hasLength(O5KeyExchange.NONCE_SIZE)
        assertThat(ke.pdmPublic).hasLength(O5KeyExchange.PUBLIC_KEY_SIZE)
    }

    @Test
    fun `constructor throws when the random nonce generator returns the wrong size`() {
        val randomByteGenerator = spy(RandomByteGenerator())
        doReturn(ByteArray(4)).whenever(randomByteGenerator).nextBytes(org.mockito.kotlin.any())

        assertThrows(PairingException::class.java) {
            newExchange(randomByteGenerator = randomByteGenerator)
        }
    }

    @Test
    fun `constructor throws when the key generator returns a wrong-size public key`() {
        val keyGenerator = spy(P256KeyGenerator())
        doReturn(ByteArray(10)).whenever(keyGenerator).publicFromPrivate(org.mockito.kotlin.any())

        assertThrows(PairingException::class.java) {
            newExchange(keyGenerator = keyGenerator)
        }
    }

    @Test
    fun `o5UpdatePodPublicData throws for a wrong-size payload`() {
        val ke = newExchange()

        assertThrows(MessageIOException::class.java) {
            ke.o5UpdatePodPublicData(ByteArray(10))
        }
    }

    @Test
    fun `o5UpdatePodPublicData splits the payload and derives 16-byte conf and ltk`() {
        val ke = newExchange()
        val podPublic = realPublicKey()
        val podNonce = ByteArray(O5KeyExchange.NONCE_SIZE) { (it + 100).toByte() }

        ke.o5UpdatePodPublicData(podPublic + podNonce)

        assertThat(ke.podPublic).isEqualTo(podPublic)
        assertThat(ke.podNonce).isEqualTo(podNonce)
        assertThat(ke.conf).hasLength(O5KeyExchange.CMAC_SIZE)
        assertThat(ke.ltk).hasLength(O5KeyExchange.CMAC_SIZE)
        assertThat(ke.conf).isNotEqualTo(ke.ltk)
    }

    @Test
    fun `getSPSNonce builds the direction-prefixed 13-byte nonce from both parties' first 6 bytes`() {
        val ke = newExchange()
        ke.o5UpdatePodPublicData(realPublicKey() + ByteArray(O5KeyExchange.NONCE_SIZE) { (it + 1).toByte() })

        val writeNonce = ke.getSPSNonce(O5KeyExchange.Direction.WRITE)
        val readNonce = ke.getSPSNonce(O5KeyExchange.Direction.READ)

        assertThat(writeNonce).hasLength(13)
        assertThat(writeNonce[0]).isEqualTo(0x01.toByte())
        assertThat(writeNonce.copyOfRange(1, 7)).isEqualTo(ke.pdmNonce.copyOfRange(0, 6))
        assertThat(writeNonce.copyOfRange(7, 13)).isEqualTo(ke.podNonce.copyOfRange(0, 6))

        assertThat(readNonce).hasLength(13)
        assertThat(readNonce[0]).isEqualTo(0x02.toByte())
        assertThat(readNonce.copyOfRange(1, 7)).isEqualTo(ke.podNonce.copyOfRange(0, 6))
        assertThat(readNonce.copyOfRange(7, 13)).isEqualTo(ke.pdmNonce.copyOfRange(0, 6))
    }

    @Test
    fun `incrementNonce increments the first 8 bytes as a little-endian counter and leaves the rest untouched`() {
        val ke = newExchange()
        val originalPdmNonce = ke.pdmNonce.copyOf()

        ke.incrementNonce(O5KeyExchange.Direction.WRITE)

        assertThat(ke.pdmNonce.copyOfRange(8, 16)).isEqualTo(originalPdmNonce.copyOfRange(8, 16))
        assertThat(ke.pdmNonce).isNotEqualTo(originalPdmNonce)

        val afterFirstIncrement = ke.pdmNonce.copyOf()
        ke.incrementNonce(O5KeyExchange.Direction.WRITE)
        assertThat(ke.pdmNonce).isNotEqualTo(afterFirstIncrement)
    }

    @Test
    fun `incrementNonce wraps a little-endian counter of all-0xFF bytes to zero`() {
        val randomByteGenerator = spy(RandomByteGenerator())
        val allFF = ByteArray(O5KeyExchange.NONCE_SIZE) { 0xFF.toByte() }
        doReturn(allFF).whenever(randomByteGenerator).nextBytes(org.mockito.kotlin.any())
        val ke = newExchange(randomByteGenerator = randomByteGenerator)

        ke.incrementNonce(O5KeyExchange.Direction.WRITE)

        assertThat(ke.pdmNonce.copyOfRange(0, 8)).isEqualTo(ByteArray(8))
        assertThat(ke.pdmNonce.copyOfRange(8, 16)).isEqualTo(ByteArray(8) { 0xFF.toByte() })
    }

    @Test
    fun `buildChannelBindingTranscript is 171 bytes composed of the documented fields in order`() {
        val ke = newExchange()
        ke.o5UpdatePodPublicData(realPublicKey() + ByteArray(O5KeyExchange.NONCE_SIZE) { (it + 1).toByte() })

        val transcript = ke.buildChannelBindingTranscript()

        assertThat(transcript).hasLength(171)
        assertThat(transcript[0]).isEqualTo(0x01.toByte())
        assertThat(transcript.copyOfRange(1, 7)).isEqualTo(O5KeyExchange.FIRMWARE_ID)
        assertThat(transcript.copyOfRange(7, 11)).isEqualTo(ByteArray(4))
        assertThat(transcript.copyOfRange(11, 75)).isEqualTo(ke.pdmPublic)
        assertThat(transcript.copyOfRange(75, 139)).isEqualTo(ke.podPublic)
        assertThat(transcript.copyOfRange(139, 155)).isEqualTo(ke.pdmNonce)
        assertThat(transcript.copyOfRange(155, 171)).isEqualTo(ke.podNonce)
    }

    @Test
    fun `buildPodChannelBindingTranscript uses a podNonce decremented by one relative to the current value`() {
        val ke = newExchange()
        ke.o5UpdatePodPublicData(realPublicKey() + ByteArray(O5KeyExchange.NONCE_SIZE) { (it + 1).toByte() })
        val originalPodNonce = ke.podNonce.copyOf()

        ke.incrementNonce(O5KeyExchange.Direction.READ)
        val transcript = ke.buildPodChannelBindingTranscript()

        assertThat(transcript).hasLength(171)
        assertThat(transcript[0]).isEqualTo(0x02.toByte())
        val podNonceAdjustedInTranscript = transcript.copyOfRange(139, 155)
        assertThat(podNonceAdjustedInTranscript).isEqualTo(originalPodNonce)
    }
}
