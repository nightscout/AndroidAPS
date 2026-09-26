package app.aaps.cgm.dexcomg7.protocol

import app.aaps.cgm.dexcomg7.protocol.crypto.G7Aes
import app.aaps.cgm.dexcomg7.protocol.crypto.G7DexcomCredentials
import app.aaps.cgm.dexcomg7.protocol.crypto.G7JPake
import app.aaps.cgm.dexcomg7.protocol.crypto.G7PCert
import app.aaps.cgm.dexcomg7.protocol.crypto.P256
import app.aaps.cgm.dexcomg7.protocol.crypto.P256Point
import app.aaps.cgm.dexcomg7.protocol.crypto.unsignedBigInteger
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * The sensor's half of the key exchange, run as the mirror of ours. If both halves reach the same
 * secret, our half really does the protocol and does not just agree with itself. Ported from the
 * SimulatedSensor in Trio's G7JPAKETests.
 */
class SimulatedJPakeSensor(pairingCode: String, seed: Int) {

    private val pin = unsignedBigInteger(pairingCode.toByteArray(Charsets.US_ASCII))
    private val privateKey1 = scalar { it + seed }
    private val privateKey2 = scalar { it * 3 + seed }
    val publicKey1: P256Point = P256.multiplyGenerator(privateKey1)
    val publicKey2: P256Point = P256.multiplyGenerator(privateKey2)

    val round1: G7PCert get() = cert(P256.generator, publicKey1, privateKey1, scalar { it * 7 + 11 })
    val round2: G7PCert get() = cert(P256.generator, publicKey2, privateKey2, scalar { it * 7 + 23 })

    fun round3(clientRound1: G7PCert, clientRound2: G7PCert): G7PCert {
        val base = P256.add(P256.add(clientRound1.publicKey, clientRound2.publicKey), publicKey1)
        val blinded = privateKey2.multiply(pin).mod(P256.order)
        return cert(base, P256.multiply(base, blinded), blinded, scalar { it * 7 + 37 })
    }

    fun sharedSecret(clientRound2: G7PCert, clientRound3: G7PCert): ByteArray {
        val blinded = privateKey2.multiply(pin).mod(P256.order)
        val unblind = BigInteger.ZERO.subtract(blinded).mod(P256.order)
        val shared = P256.multiply(P256.add(clientRound3.publicKey, P256.multiply(clientRound2.publicKey, unblind)), privateKey2)
        return G7JPake.sha256(shared.x.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else ByteArray(32 - it.size) + it })
    }

    private fun scalar(byteAt: (Int) -> Int): BigInteger =
        unsignedBigInteger(ByteArray(32) { byteAt(it).toByte() }).mod(P256.order - BigInteger.TWO) + BigInteger.ONE

    private fun cert(base: P256Point, publicKey: P256Point, privateKey: BigInteger, randomizer: BigInteger): G7PCert {
        val proofPoint = P256.multiply(base, randomizer)
        val challenge = G7JPake.transcriptHash(base, proofPoint, publicKey, G7JPake.PEER_PARTY)
        return G7PCert(publicKey, proofPoint, randomizer.subtract(challenge.multiply(privateKey)).mod(P256.order))
    }
}

/**
 * A whole sensor on the other end of a [G7Link]: it answers the pairing handshake the way the real
 * one does (as far as Trio's handshake describes it) and checks our signature with the public key.
 *
 * Notifications are delivered inside the write call. That is enough, because the authenticator puts
 * its listeners in place before each request, exactly as it must with a real sensor.
 */
class SimulatedG7Sensor(
    private val pairingCode: String,
    /** The key this sensor holds for us from an earlier pairing, if any. */
    var storedKey: ByteArray? = null,
    /** Refuse every session with this reason, after the challenge. */
    private val refuseWith: G7AuthFailureCode? = null,
    override val address: String = "AA:BB:CC:DD:EE:FF",
    override val name: String? = "Dexcom12"
) : G7Link {

    private val jpake = SimulatedJPakeSensor(pairingCode, seed = 5)
    private val listeners = mutableMapOf<G7Characteristic, (ByteArray) -> Unit>()
    private val received = ByteArrayOutputStream()
    private var sensorChallenge: ByteArray? = null
    private var keyChallengeBytes: ByteArray? = null

    val enabled = mutableSetOf<G7Characteristic>()
    val bondRequested get() = bondRequestCount > 0
    var bondRequestCount = 0
        private set
    var signatureVerified = false
        private set
    override var isConnected = true

    /** The certificates we sent, as received. */
    val receivedCertificates = mutableListOf<ByteArray>()

    override suspend fun enableNotifications(characteristic: G7Characteristic) {
        enabled.add(characteristic)
    }

    override fun setListener(characteristic: G7Characteristic, listener: ((ByteArray) -> Unit)?) {
        if (listener == null) listeners.remove(characteristic) else listeners[characteristic] = listener
    }

    override suspend fun onBondRequested() {
        bondRequestCount++
    }

    private fun notify(characteristic: G7Characteristic, data: ByteArray) {
        listeners[characteristic]?.invoke(data)
    }

    private fun stream(data: ByteArray) {
        data.toList().chunked(20).forEach { notify(G7Characteristic.CERTIFICATE, it.toByteArray()) }
    }

    private var pendingCertificateLength = 0

    override suspend fun write(characteristic: G7Characteristic, value: ByteArray, withResponse: Boolean) {
        when (characteristic) {
            G7Characteristic.CERTIFICATE -> {
                received.write(value)
                if (pendingCertificateLength > 0 && received.size() >= pendingCertificateLength) {
                    receivedCertificates.add(received.toByteArray().copyOf(pendingCertificateLength))
                    received.reset()
                    pendingCertificateLength = 0
                }
                keyChallengeBytes?.let { challenge ->
                    if (received.size() >= 64) {
                        signatureVerified = verify(challenge, received.toByteArray().copyOf(64))
                        received.reset()
                        keyChallengeBytes = null
                    }
                }
            }

            G7Characteristic.AUTHENTICATION -> handleAuthentication(value)
            else -> Unit
        }
    }

    private val clientRounds = mutableListOf<G7PCert>()

    private fun takeClientRound() {
        // Each client round is 160 bytes sent after the sensor's round of the same number.
        if (received.size() >= G7PCert.BYTE_COUNT) {
            clientRounds.add(G7PCert.decode(received.toByteArray().copyOf(G7PCert.BYTE_COUNT)))
            received.reset()
        }
    }

    private fun handleAuthentication(value: ByteArray) {
        when (value.u8(0)) {
            0x0A -> {
                takeClientRound()
                when (value.u8(1)) {
                    0 -> stream(jpake.round1.encoded)
                    1 -> stream(jpake.round2.encoded)
                    2 -> stream(jpake.round3(clientRounds[0], clientRounds[1]).encoded)
                }
                notify(G7Characteristic.AUTHENTICATION, byteArrayOf(0x0A, 0x00))
            }

            0x02 -> {
                // A fresh exchange ends with our round 3 in the buffer.
                takeClientRound()
                if (clientRounds.size == 3) storedKey = jpake.sharedSecret(clientRounds[1], clientRounds[2]).copyOf(16)
                val key = storedKey ?: ByteArray(16)
                val ours = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x08)
                sensorChallenge = ours
                notify(G7Characteristic.AUTHENTICATION, byteArrayOf(0x03) + G7Aes.encryptChallenge(value.copyOfRange(1, 9), key) + ours)
            }

            0x04 -> {
                val key = storedKey ?: ByteArray(16)
                val expected = G7Aes.encryptChallenge(sensorChallenge!!, key)
                val status = when {
                    refuseWith != null                                  -> byteArrayOf(0x05, 0x02, refuseWith.code.toByte())
                    !expected.contentEquals(value.copyOfRange(1, 9))    -> byteArrayOf(0x05, 0x02, G7AuthFailureCode.CHALLENGE_MISMATCH.code.toByte())
                    clientRounds.size == 3                              -> byteArrayOf(0x05, 0x01, 0x02)
                    else                                                -> byteArrayOf(0x05, 0x01, 0x01)
                }
                notify(G7Characteristic.AUTHENTICATION, status)
            }

            0x0B -> {
                val index = value.u8(1)
                val length = value.u16le(2)
                if (length == 0) {
                    notify(G7Characteristic.AUTHENTICATION, byteArrayOf(0x0B, 0x00))
                } else {
                    pendingCertificateLength = length
                    val sensorCertificate = ByteArray(300) { it.toByte() }
                    stream(sensorCertificate)
                    notify(G7Characteristic.AUTHENTICATION, byteArrayOf(0x0B, 0x00, index.toByte()) + sensorCertificate.size.u16leBytes())
                }
            }

            0x0C -> {
                val toSign = ByteArray(16) { (0xA0 + it).toByte() }
                keyChallengeBytes = toSign
                stream(ByteArray(64) { 0x5A })
                notify(G7Characteristic.AUTHENTICATION, byteArrayOf(0x0C, 0x00) + toSign)
            }

            0x06 -> notify(G7Characteristic.AUTHENTICATION, byteArrayOf(0x06, 0x00))

            0x07 -> {
                notify(G7Characteristic.AUTHENTICATION, byteArrayOf(0x07, 0x00))
                notify(G7Characteristic.AUTHENTICATION, byteArrayOf(0x08, 0x00))
            }
        }
    }

    private fun verify(signed: ByteArray, raw: ByteArray): Boolean {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spkiFor(G7DexcomCredentials.challengePublicKey)))
        return Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initVerify(publicKey)
            update(signed)
            verify(raw)
        }
    }

    companion object {

        /** SubjectPublicKeyInfo header for an uncompressed P-256 key. */
        private val P256_SPKI_PREFIX = "3059301306072a8648ce3d020106082a8648ce3d030107034200".hexToBytes()

        fun spkiFor(uncompressed: ByteArray) = P256_SPKI_PREFIX + uncompressed
    }
}
