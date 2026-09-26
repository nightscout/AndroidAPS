package app.aaps.cgm.dexcomg7.protocol.crypto

import app.aaps.cgm.dexcomg7.protocol.SimulatedG7Sensor
import app.aaps.cgm.dexcomg7.protocol.SimulatedJPakeSensor
import app.aaps.cgm.dexcomg7.protocol.hexToBytes
import app.aaps.cgm.dexcomg7.protocol.toHex
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/** P-256 maths, EC-JPAKE, AES challenge and signing. Ported from Trio's crypto tests, plus checks against the JDK. */
class G7CryptoTest {

    @Test
    fun generatorIsOnTheCurve() {
        assertThat(P256.isOnCurve(P256.generator)).isTrue()
    }

    @Test
    fun scalarMultiplicationMatchesTheJdk() {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        repeat(5) {
            val pair = generator.generateKeyPair()
            val s = (pair.private as ECPrivateKey).s
            val w = (pair.public as ECPublicKey).w
            val point = P256.multiplyGenerator(s)
            assertThat(point.x).isEqualTo(w.affineX)
            assertThat(point.y).isEqualTo(w.affineY)
            assertThat(P256.isOnCurve(point)).isTrue()
        }
    }

    @Test
    fun additionIsAdditionOfScalars() {
        val a = BigInteger("123456789abcdef", 16)
        val b = BigInteger("fedcba987654321", 16)
        assertThat(P256.add(P256.multiplyGenerator(a), P256.multiplyGenerator(b))).isEqualTo(P256.multiplyGenerator(a + b))
        val point = P256.multiplyGenerator(a)
        assertThat(P256.add(point, point)).isEqualTo(P256.multiply(point, BigInteger.TWO))
    }

    @Test
    fun edgeCasesGiveInfinity() {
        assertThat(P256.multiplyGenerator(P256.order).isInfinity).isTrue()
        assertThat(P256.multiplyGenerator(BigInteger.ZERO).isInfinity).isTrue()
        val point = P256.multiplyGenerator(BigInteger.valueOf(42))
        assertThat(P256.add(point, P256.negate(point)).isInfinity).isTrue()
    }

    @Test
    fun uncompressedEncoding() {
        val encoded = P256.generator.uncompressedBytes
        assertThat(encoded).hasLength(65)
        assertThat(encoded[0]).isEqualTo(0x04.toByte())
        assertThat(encoded.copyOfRange(1, 33).toHex()).isEqualTo("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296")
    }

    private class Exchange(val client: ByteArray, val sensor: ByteArray, val jpake: G7JPake, val sensor1: G7PCert, val sensor3: G7PCert)

    private fun exchange(clientCode: String, sensorCode: String, seed: Int = 5): Exchange {
        val jpake = G7JPake(clientCode)
        val sensor = SimulatedJPakeSensor(sensorCode, seed)
        val sensorRound1 = sensor.round1
        val clientRound1 = G7PCert.decode(jpake.makeRound1())
        val sensorRound2 = sensor.round2
        val clientRound2 = G7PCert.decode(jpake.makeRound2())
        val sensorRound3 = sensor.round3(clientRound1, clientRound2)
        val clientSecret = jpake.deriveSharedSecret(sensorRound2, sensorRound3)
        val clientRound3 = G7PCert.decode(jpake.makeRound3(sensorRound1, sensorRound2))
        return Exchange(clientSecret, sensor.sharedSecret(clientRound2, clientRound3), jpake, sensorRound1, sensorRound3)
    }

    @Test
    fun bothSidesReachTheSameSecret() {
        val result = exchange("1155", "1155")
        assertThat(result.client).hasLength(32)
        assertThat(result.client.toHex()).isEqualTo(result.sensor.toHex())
    }

    @Test
    fun secretDependsOnTheSensorKeys() {
        assertThat(exchange("1155", "1155", 5).client.toHex()).isNotEqualTo(exchange("1155", "1155", 99).client.toHex())
    }

    @Test
    fun wrongCodeGivesDifferentSecrets() {
        val result = exchange("1155", "9999")
        assertThat(result.client.toHex()).isNotEqualTo(result.sensor.toHex())
    }

    @Test
    fun sensorProofsCheckOut() {
        val result = exchange("1155", "1155")
        assertThat(result.jpake.validateRound1Or2(result.sensor1)).isTrue()
        assertThat(result.jpake.validateRound3(result.sensor1, result.sensor3)).isTrue()
    }

    @Test
    fun tamperedProofIsRefused() {
        val result = exchange("1155", "1155")
        val tampered = result.sensor1.copy(proof = (result.sensor1.proof + BigInteger.ONE).mod(P256.order))
        assertThat(result.jpake.validateRound1Or2(tampered)).isFalse()
    }

    @Test
    fun ourProofsWouldCheckOutAtTheSensor() {
        val round1 = G7PCert.decode(G7JPake("1155").makeRound1())
        val challenge = G7JPake.transcriptHash(P256.generator, round1.proofPoint, round1.publicKey, G7JPake.OWN_PARTY)
        val recomputed = P256.add(P256.multiply(P256.generator, round1.proof), P256.multiply(round1.publicKey, challenge))
        assertThat(recomputed).isEqualTo(round1.proofPoint)
    }

    @Test
    fun round3UsesTheFixedRandomizer() {
        fun makeRound3(): G7PCert {
            val jpake = G7JPake("1155") { ByteArray(it) { 0x42 } }
            val sensor = SimulatedJPakeSensor("1155", 5)
            jpake.makeRound1()
            jpake.makeRound2()
            return G7PCert.decode(jpake.makeRound3(sensor.round1, sensor.round2))
        }
        assertThat(makeRound3().proofPoint).isEqualTo(makeRound3().proofPoint)
    }

    @Test
    fun certEncodingRoundTrips() {
        val encoded = G7JPake("1155").makeRound1()
        assertThat(encoded).hasLength(G7PCert.BYTE_COUNT)
        val decoded = G7PCert.decode(encoded)
        assertThat(decoded.encoded.toHex()).isEqualTo(encoded.toHex())
        assertThat(P256.isOnCurve(decoded.publicKey)).isTrue()
        assertThat(P256.isOnCurve(decoded.proofPoint)).isTrue()
        assertThrows(IllegalArgumentException::class.java) { G7PCert.decode(ByteArray(159)) }
        assertThrows(IllegalArgumentException::class.java) { G7PCert.decode(ByteArray(161)) }
    }

    @Test
    fun outOfOrderUseFails() {
        val jpake = G7JPake("1155")
        val sensor = SimulatedJPakeSensor("1155", 5)
        assertThrows(IllegalStateException::class.java) { jpake.makeRound3(sensor.round1, sensor.round2) }
        assertThat(jpake.validateRound3(sensor.round1, sensor.round1)).isFalse()
    }

    /** Vectors made with `openssl enc -aes-128-ecb -nopad`, independent of this code. */
    @Test
    fun aesKnownAnswers() {
        assertThat(G7Aes.encryptChallenge("0011223344556677".hexToBytes(), "000102030405060708090a0b0c0d0e0f".hexToBytes()).toHex()).isEqualTo("28b454bdf4d00600")
        assertThat(G7Aes.encryptChallenge("a1b2c3d4e5f60718".hexToBytes(), "ffeeddccbbaa99887766554433221100".hexToBytes()).toHex()).isEqualTo("19b22de101061935")
        assertThrows(IllegalArgumentException::class.java) { G7Aes.encryptChallenge(ByteArray(7), ByteArray(16)) }
        assertThrows(IllegalArgumentException::class.java) { G7Aes.encryptChallenge(ByteArray(8), ByteArray(15)) }
    }

    @Test
    fun embeddedKeyPairMatches() {
        val publicKey = P256.multiplyGenerator(BigInteger(1, G7DexcomCredentials.challengePrivateKey))
        assertThat(publicKey.uncompressedBytes.toHex()).isEqualTo(G7DexcomCredentials.challengePublicKey.toHex())
        assertThat(G7DexcomCredentials.challengePrivateKey).hasLength(32)
    }

    @Test
    fun signatureChecksOutWithTheEmbeddedPublicKey() {
        val acknowledgement = byteArrayOf(0x0C, 0x00) + ByteArray(16) { it.toByte() } + byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        val signature = G7ChallengeSigner.sign(acknowledgement)
        assertThat(signature).hasLength(64)
        val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(SimulatedG7Sensor.spkiFor(G7DexcomCredentials.challengePublicKey)))
        val ok = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initVerify(key)
            update(acknowledgement.copyOfRange(2, 18))
            verify(signature)
        }
        assertThat(ok).isTrue()
        assertThrows(IllegalArgumentException::class.java) { G7ChallengeSigner.sign(ByteArray(17)) }
    }

    @Test
    fun certificatesAreWellFormedDer() {
        val certificates = G7DexcomCredentials.certificates
        assertThat(certificates.map { it.size }).containsExactly(494, 465).inOrder()
        for (certificate in certificates) {
            assertThat(certificate[0]).isEqualTo(0x30.toByte())
            assertThat(certificate[1]).isEqualTo(0x82.toByte())
            val declared = ((certificate[2].toInt() and 0xff) shl 8) or (certificate[3].toInt() and 0xff)
            assertThat(declared + 4).isEqualTo(certificate.size)
        }
        assertThat(certificates[1].toHex()).contains(G7DexcomCredentials.challengePublicKey.toHex())
    }
}
