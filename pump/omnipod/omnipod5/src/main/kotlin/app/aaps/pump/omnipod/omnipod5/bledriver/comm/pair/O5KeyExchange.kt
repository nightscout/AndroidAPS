package app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.omnipod5.bledriver.pod.util.P256KeyGenerator
import app.aaps.pump.omnipod.common.bledriver.pod.util.RandomByteGenerator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Runs the Omnipod 5 pairing key exchange (the "SPS1"/"SPS2" handshake): generates the
 * controller's ephemeral P-256 keypair and nonce, combines them with the pod's ephemeral
 * public key and nonce plus the ECDH shared secret into a SHA-256-derived confirmation
 * value and long-term key (LTK), and provides the nonce/transcript machinery the rest of
 * the O5 pairing flow needs to build and verify SPS2/SPS2.1 messages.
 *
 * Ported from OmnipodKit's O5KeyExchange.swift (loopandlearn/OmnipodKit).
 */
class O5KeyExchange(
    private val aapsLogger: AAPSLogger,
    private val p256KeyGenerator: P256KeyGenerator,
    randomByteGenerator: RandomByteGenerator,
    /** The certificate-derived controller ID (4 bytes, big-endian). */
    val controllerIdData: ByteArray
) {

    enum class Direction { WRITE, READ }

    var pdmNonce: ByteArray = randomByteGenerator.nextBytes(NONCE_SIZE)
        private set
    val pdmPrivate: ByteArray = p256KeyGenerator.generatePrivateKey()
    val pdmPublic: ByteArray = p256KeyGenerator.publicFromPrivate(pdmPrivate)

    var podPublic: ByteArray = ByteArray(PUBLIC_KEY_SIZE)
        private set
    var podNonce: ByteArray = ByteArray(NONCE_SIZE)
        private set

    var conf: ByteArray = ByteArray(CMAC_SIZE)
        private set
    var ltk: ByteArray = ByteArray(CMAC_SIZE)
        private set

    init {
        if (pdmNonce.size != NONCE_SIZE) {
            throw PairingException("pdmNonce size ${pdmNonce.size} != expected $NONCE_SIZE")
        }
        if (pdmPublic.size != PUBLIC_KEY_SIZE) {
            throw PairingException("pdmPublic size ${pdmPublic.size} != expected $PUBLIC_KEY_SIZE")
        }
        aapsLogger.debug(
            LTag.PUMPBTCOMM,
            "O5KeyExchange init: controllerId=${controllerIdData.joinToString("") { "%02x".format(it) }}"
        )
    }

    /**
     * Processes the pod's SPS1 response payload (its ephemeral public key + nonce),
     * then derives [conf] and [ltk] from the ECDH shared secret.
     *
     * @throws MessageIOException if [payload] isn't exactly PUBLIC_KEY_SIZE + NONCE_SIZE bytes.
     */
    fun o5UpdatePodPublicData(payload: ByteArray) {
        if (payload.size != PUBLIC_KEY_SIZE + NONCE_SIZE) {
            throw MessageIOException(
                "Invalid SPS1 payload size: ${payload.size}, expected ${PUBLIC_KEY_SIZE + NONCE_SIZE}"
            )
        }
        podPublic = payload.copyOfRange(0, PUBLIC_KEY_SIZE)
        podNonce = payload.copyOfRange(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE + NONCE_SIZE)
        aapsLogger.debug(LTag.PUMPBTCOMM, "pdmPublic (${pdmPublic.size}): ${pdmPublic.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "podPublic (${podPublic.size}): ${podPublic.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "pdmNonce (${pdmNonce.size}): ${pdmNonce.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "podNonce (${podNonce.size}): ${podNonce.toHex()}")
        o5GenerateKeys()
    }

    private fun o5GenerateKeys() {
        val sharedSecret = p256KeyGenerator.computeSharedSecret(pdmPrivate, podPublic)

        val data = ByteArrayBuilder()
            .appendBigEndianU64Length(FIRMWARE_ID.size)
            .append(FIRMWARE_ID)
            .appendBigEndianU64Length(4)
            .append(byteArrayOf(0x00, 0x00, 0x00, 0x00))
            .appendBigEndianU64Length(pdmPublic.size)
            .append(pdmPublic)
            .appendBigEndianU64Length(podPublic.size)
            .append(podPublic)
            .appendBigEndianU64Length(sharedSecret.size)
            .append(sharedSecret)
            .toByteArray()

        val derivedKey = MessageDigest.getInstance("SHA-256").digest(data)
        if (derivedKey.size != 32) {
            throw PairingException("SHA-256 output size ${derivedKey.size} != expected 32")
        }
        conf = derivedKey.copyOfRange(0, 16)
        ltk = derivedKey.copyOfRange(16, 32)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Derived conf (${conf.size}): ${conf.toHex()}, ltk ${ltk.size} bytes")
    }

    /**
     * Builds the 13-byte AES-CCM nonce for an SPS2/SPS2.1 message in the given [direction]:
     * `[directionByte] || first6(senderNonce) || first6(receiverNonce)`.
     */
    fun getSPSNonce(direction: Direction): ByteArray {
        if (pdmNonce.size < 6 || podNonce.size < 6) {
            return ByteArray(0)
        }
        val pdmSlice = pdmNonce.copyOfRange(0, 6)
        val podSlice = podNonce.copyOfRange(0, 6)

        val nonce = when (direction) {
            Direction.WRITE -> byteArrayOf(0x01) + pdmSlice + podSlice
            Direction.READ  -> byteArrayOf(0x02) + podSlice + pdmSlice
        }
        return nonce
    }

    /** Increments the controller's (write) or pod's (read) nonce by one, per [incrementNonceInPlace]. */
    fun incrementNonce(direction: Direction) {
        when (direction) {
            Direction.WRITE -> pdmNonce = incrementNonceInPlace(pdmNonce)
            Direction.READ  -> podNonce = incrementNonceInPlace(podNonce)
        }
    }

    /**
     * Increments the first 8 bytes of [nonce], interpreted as a little-endian UInt64
     * counter (matching Swift's `withUnsafeBytes { $0.load(as: UInt64.self) }` on
     * little-endian Apple hardware), wrapping on overflow. Bytes 8+ are preserved as-is.
     */
    private fun incrementNonceInPlace(nonce: ByteArray): ByteArray {
        val counter = ByteBuffer.wrap(nonce, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val incremented = counter + 1
        val result = nonce.copyOf()
        ByteBuffer.wrap(result, 0, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(incremented)
        return result
    }

    /**
     * Decrements the first 8 bytes of [nonce] (little-endian UInt64), wrapping on
     * underflow, leaving the rest of the bytes untouched. Used only when building the
     * pod's channel-binding transcript variant (see [buildPodChannelBindingTranscript]).
     */
    private fun decrementNonceCopy(nonce: ByteArray): ByteArray {
        val counter = ByteBuffer.wrap(nonce, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
        val decremented = counter - 1
        val result = nonce.copyOf()
        ByteBuffer.wrap(result, 0, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(decremented)
        return result
    }

    /**
     * Builds the 171-byte channel-binding transcript for SPS2.1 signing, from the
     * controller's point of view:
     * `[0x01] || FIRMWARE_ID(6) || zeros(4) || pdmPublic(64) || podPublic(64) || pdmNonce(16) || podNonce(16)`
     */
    fun buildChannelBindingTranscript(): ByteArray {
        val transcript = byteArrayOf(0x01) +
            FIRMWARE_ID +
            byteArrayOf(0x00, 0x00, 0x00, 0x00) +
            pdmPublic +
            podPublic +
            pdmNonce +
            podNonce

        check(transcript.size == TRANSCRIPT_SIZE) {
            "Channel-binding transcript size mismatch: got ${transcript.size}, expected $TRANSCRIPT_SIZE"
        }
        return transcript
    }

    /**
     * Builds the pod's (peer) variant of the channel-binding transcript, for verifying the
     * pod's own SPS2 signature. Must only be called AFTER: controller sent SPS2.1 (pdmNonce
     * +1), received pod SPS2.1 (podNonce +1), sent SPS2 (pdmNonce +2 total), received pod
     * SPS2 (podNonce +2 total) - i.e. from [O5LTKExchanger]'s `o5validatePodSps2()` step.
     *
     * At that point the pod's own nonce state when it built ITS transcript was one step
     * behind the controller's current podNonce (the pod had only processed SPS2.1, not yet
     * encrypted its SPS2 reply), so [podNonce] is decremented by one here to match.
     *
     * Layout: `[0x02] || zeros(4) || FIRMWARE_ID(6) || podPublic(64) || pdmPublic(64) || podNonceAdjusted(16) || pdmNonce(16)`
     */
    fun buildPodChannelBindingTranscript(): ByteArray {
        val podNonceAdjusted = decrementNonceCopy(podNonce)

        val transcript = byteArrayOf(0x02) +
            byteArrayOf(0x00, 0x00, 0x00, 0x00) +
            FIRMWARE_ID +
            podPublic +
            pdmPublic +
            podNonceAdjusted +
            pdmNonce

        check(transcript.size == TRANSCRIPT_SIZE) {
            "Pod channel-binding transcript size mismatch: got ${transcript.size}, expected $TRANSCRIPT_SIZE"
        }
        return transcript
    }

    /** A tiny append-only byte builder, just to keep [o5GenerateKeys] readable. */
    private class ByteArrayBuilder {
        private val parts = mutableListOf<ByteArray>()

        fun append(bytes: ByteArray): ByteArrayBuilder {
            parts.add(bytes)
            return this
        }

        /** Appends [value] as an 8-byte big-endian length field (matches Swift's
         *  `UInt64(...).bigEndian` length-prefixing in the KDF input construction). */
        fun appendBigEndianU64Length(value: Int): ByteArrayBuilder {
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            buffer.putLong(value.toLong())
            parts.add(buffer.array())
            return this
        }

        fun toByteArray(): ByteArray {
            val total = parts.fold(0) { acc, part -> acc + part.size }
            val result = ByteArray(total)
            var offset = 0
            for (part in parts) {
                part.copyInto(result, offset)
                offset += part.size
            }
            return result
        }
    }

    companion object {

        const val CMAC_SIZE = 16
        const val PUBLIC_KEY_SIZE = 64
        const val NONCE_SIZE = 16
        private const val TRANSCRIPT_SIZE = 171

        /**
         * Fixed 6-byte value taken from the PDM firmware. Shared with [O5LTKExchanger]
         * (not yet ported), which will reference this same constant rather than
         * duplicating it.
         */
        val FIRMWARE_ID: ByteArray = hexToBytes("9b0ab96a76f4")

        private fun hexToBytes(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}
