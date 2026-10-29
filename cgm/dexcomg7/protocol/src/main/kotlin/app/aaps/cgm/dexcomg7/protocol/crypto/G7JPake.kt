package app.aaps.cgm.dexcomg7.protocol.crypto

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * One round of the sensor's key exchange as sent over the air: two P-256 points and a Schnorr proof,
 * each value 32 bytes big endian, 160 bytes in all. [publicKey] is the value being proven and
 * [proofPoint] the commitment the verifier recomputes.
 */
data class G7PCert(val publicKey: P256Point, val proofPoint: P256Point, val proof: BigInteger) {

    val encoded: ByteArray
        get() = publicKey.x.toFixedBytes(32) + publicKey.y.toFixedBytes(32) +
            proofPoint.x.toFixedBytes(32) + proofPoint.y.toFixedBytes(32) + proof.toFixedBytes(32)

    companion object {

        const val BYTE_COUNT = 160

        fun decode(data: ByteArray): G7PCert {
            require(data.size == BYTE_COUNT) { "A key exchange round is $BYTE_COUNT bytes, got ${data.size}" }
            fun value(index: Int) = unsignedBigInteger(data.copyOfRange(index * 32, index * 32 + 32))
            return G7PCert(P256Point(value(0), value(1)), P256Point(value(2), value(3)), value(4))
        }
    }
}

/**
 * Our side of the key exchange a G7-family sensor runs at pairing: EC-JPAKE over P-256, where the
 * shared low-entropy secret is the 4-digit code on the applicator.
 *
 * Both sides prove they know the code without sending it. The result is a 256-bit secret; its first
 * 16 bytes are the AES-128 key used for every later reconnect.
 *
 * Differences from textbook EC-JPAKE, all required by the sensor: fixed party names, a fixed
 * randomizer in our round-3 proof, and a transcript hash over length-prefixed uncompressed points in a
 * fixed order.
 *
 * Ported from Trio's G7SensorKit (G7JPAKE.swift, MIT, LoopKit Authors). The protocol knowledge comes
 * from Juggluco and xDrip.
 */
class G7JPake(pairingCode: String, private val random: (Int) -> ByteArray = ::secureRandomBytes) {

    /** The pairing code as a big endian number over its ASCII digits. */
    private val pin = unsignedBigInteger(pairingCode.toByteArray(Charsets.US_ASCII))

    private var keyPair1: Pair<BigInteger, P256Point>? = null
    private var keyPair2: Pair<BigInteger, P256Point>? = null

    fun makeRound1(): ByteArray {
        val pair = makeKeyPair().also { keyPair1 = it }
        return makeCert(P256.generator, pair.second, pair.first).encoded
    }

    fun makeRound2(): ByteArray {
        val pair = makeKeyPair().also { keyPair2 = it }
        return makeCert(P256.generator, pair.second, pair.first).encoded
    }

    /** Our key confirmation round, made from both of the sensor's first two rounds. */
    fun makeRound3(peerRound1: G7PCert, peerRound2: G7PCert): ByteArray {
        val pair1 = keyPair1 ?: throw IllegalStateException("Round 1 not made")
        val pair2 = keyPair2 ?: throw IllegalStateException("Round 2 not made")
        val blindedKey = pair2.first.multiply(pin).mod(P256.order)
        val base = P256.add(P256.add(pair1.second, peerRound1.publicKey), peerRound2.publicKey)
        val publicKey = P256.multiply(base, blindedKey)
        return makeCert(base, publicKey, blindedKey, ROUND3_RANDOMIZER).encoded
    }

    /** The agreed secret: SHA-256 of the x coordinate of the shared point. The first 16 bytes are the key. */
    fun deriveSharedSecret(peerRound2: G7PCert, peerRound3: G7PCert): ByteArray {
        val pair2 = keyPair2 ?: throw IllegalStateException("Round 2 not made")
        // Take our own blinding out of the sensor's round 3, then apply our round 2 key.
        val blindedKey = pair2.first.multiply(pin).mod(P256.order)
        val unblind = BigInteger.ZERO.subtract(blindedKey).mod(P256.order)
        val shared = P256.multiply(
            P256.add(peerRound3.publicKey, P256.multiply(peerRound2.publicKey, unblind)),
            pair2.first
        )
        return sha256(shared.x.toFixedBytes(32))
    }

    /** Whether a sensor round 1 or 2 carries a valid proof. Advisory only: the AES challenge is the real check. */
    fun validateRound1Or2(cert: G7PCert): Boolean = verifyProof(P256.generator, cert, PEER_PARTY)

    /** Whether the sensor's round 3 carries a valid proof. Advisory only. */
    fun validateRound3(peerRound1: G7PCert, peerRound3: G7PCert): Boolean {
        val pair1 = keyPair1 ?: return false
        val pair2 = keyPair2 ?: return false
        val base = P256.add(P256.add(pair1.second, pair2.second), peerRound1.publicKey)
        return verifyProof(base, peerRound3, PEER_PARTY)
    }

    private fun makeKeyPair(): Pair<BigInteger, P256Point> {
        val privateKey = randomScalar()
        return privateKey to P256.multiplyGenerator(privateKey)
    }

    /** Uniform in [1, order - 2], the range the sensor uses too. */
    private fun randomScalar(): BigInteger = unsignedBigInteger(random(32)).mod(P256.order - BigInteger.TWO) + BigInteger.ONE

    private fun makeCert(base: P256Point, publicKey: P256Point, privateKey: BigInteger, randomizer: BigInteger = randomScalar()): G7PCert {
        val proofPoint = P256.multiply(base, randomizer)
        val challenge = transcriptHash(base, proofPoint, publicKey, OWN_PARTY)
        val proof = randomizer.subtract(challenge.multiply(privateKey)).mod(P256.order)
        return G7PCert(publicKey, proofPoint, proof)
    }

    private fun verifyProof(base: P256Point, cert: G7PCert, party: ByteArray): Boolean {
        val challenge = transcriptHash(base, cert.proofPoint, cert.publicKey, party)
        val recomputed = P256.add(P256.multiply(base, cert.proof), P256.multiply(cert.publicKey, challenge))
        return recomputed == cert.proofPoint
    }

    companion object {

        /** Party name on the proofs we make. */
        val OWN_PARTY: ByteArray = "client".toByteArray(Charsets.US_ASCII)

        /** Party name on the proofs the sensor makes. */
        val PEER_PARTY = byteArrayOf(0x37, 0x56, 0x27, 0x67, 0x56, 0x27)

        /** The fixed randomizer the sensor expects in our round-3 proof. */
        val ROUND3_RANDOMIZER = BigInteger("fbc971b837e9491e45a4179ed33865c508a1e0a1d350f5af0f96370695fdc393", 16)

        private val secureRandom = SecureRandom()

        fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also { secureRandom.nextBytes(it) }

        /** SHA-256 over the three points and the party name, each with a 4-byte big endian length, reduced mod order. */
        fun transcriptHash(base: P256Point, proofPoint: P256Point, publicKey: P256Point, party: ByteArray): BigInteger {
            val digest = MessageDigest.getInstance("SHA-256")
            for (bytes in listOf(base.uncompressedBytes, proofPoint.uncompressedBytes, publicKey.uncompressedBytes, party)) {
                digest.update(lengthPrefix(bytes.size))
                digest.update(bytes)
            }
            return unsignedBigInteger(digest.digest()).mod(P256.order)
        }

        private fun lengthPrefix(length: Int) =
            byteArrayOf((length shr 24).toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte())

        fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
