package app.aaps.core.nssdk.utils

import app.aaps.core.nssdk.localmodel.clientcontrol.AckEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.ProgressEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.SignedEnvelope
import app.aaps.core.nssdk.utils.ClientControlCrypto.timestampWithinSkew
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Sign / verify primitive for the client-control channel.
 *
 * HMAC-SHA256 over [SignedEnvelope.canonicalString]. Signatures are hex-
 * encoded for prefs-friendly transport. Verification uses constant-time
 * comparison on the byte representations to deny timing oracles.
 *
 * Identifiers and secrets are produced by [SecureRandom] / [UUID]. A 32-byte
 * secret matches HMAC-SHA256's preferred key length and survives birthday
 * bounds for the lifetime of any reasonable pairing.
 *
 * **Threat model — replay over Nightscout transport.** [timestampWithinSkew]
 * uses a ±5 min window. NS-mediated delivery can be delayed by minutes when
 * the master is offline or the NS instance is busy, so the window alone is
 * not a strong replay defence: an attacker can intercept a message and replay
 * it before the master has seen the original. Defence-in-depth therefore
 * relies on the strictly-monotonic counter check the receiver MUST perform
 * (see `AuthorizedClientsRepository.secretLookup`) — a replayed message has a
 * counter ≤ the last accepted, so it is rejected even within the skew window.
 * Skew alone defends only against forgery of a far-future or far-past
 * timestamp.
 */
object ClientControlCrypto {

    private const val HMAC_ALGO = "HmacSHA256"
    private const val SECRET_BYTES = 32
    private const val MAX_TIMESTAMP_SKEW_MS = 5L * 60L * 1000L

    private val secureRandom = SecureRandom()

    fun newSecretBytes(): ByteArray = ByteArray(SECRET_BYTES).also(secureRandom::nextBytes)

    fun newClientId(): String = UUID.randomUUID().toString()

    /** Lower-case, zero-padded hex encoding of [bytes] (two chars per byte). Inverse of [hexToBytes]. */
    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { "%02x".format(it) }

    /**
     * Decode a hex string back to bytes. Inverse of [bytesToHex].
     *
     * @throws IllegalArgumentException if [hex] has an odd length or contains any non-hex character.
     */
    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Odd-length hex string" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Non-hex character in input" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    fun sign(secret: ByteArray, canonical: String): String {
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(SecretKeySpec(secret, HMAC_ALGO))
        return bytesToHex(mac.doFinal(canonical.toByteArray(Charsets.UTF_8)))
    }

    fun signEnvelope(secret: ByteArray, draft: SignedEnvelope): SignedEnvelope =
        draft.copy(signature = sign(secret, draft.canonicalString()))

    fun verifyEnvelope(secret: ByteArray, env: SignedEnvelope): Boolean {
        val expected = sign(secret, env.canonicalString())
        return constantTimeEquals(expected, env.signature)
    }

    /** Master-side: sign a drafted [AckEnvelope] (signature field ignored on input). */
    fun signAck(secret: ByteArray, draft: AckEnvelope): AckEnvelope =
        draft.copy(signature = sign(secret, draft.canonicalString()))

    /** Client-side: verify an [AckEnvelope] came from the paired master (same shared secret). */
    fun verifyAck(secret: ByteArray, ack: AckEnvelope): Boolean {
        val expected = sign(secret, ack.canonicalString())
        return constantTimeEquals(expected, ack.signature)
    }

    /** Master-side: sign a drafted [ProgressEnvelope] (signature field ignored on input). */
    fun signProgress(secret: ByteArray, draft: ProgressEnvelope): ProgressEnvelope =
        draft.copy(signature = sign(secret, draft.canonicalString()))

    /** Client-side: verify a [ProgressEnvelope] came from the paired master (same shared secret). */
    fun verifyProgress(secret: ByteArray, env: ProgressEnvelope): Boolean {
        val expected = sign(secret, env.canonicalString())
        return constantTimeEquals(expected, env.signature)
    }

    /**
     * Range check on a received timestamp. `now` is master clock millis.
     * Returns true when the message was minted within ±5 min of now.
     */
    fun timestampWithinSkew(timestamp: Long, now: Long): Boolean =
        kotlin.math.abs(now - timestamp) <= MAX_TIMESTAMP_SKEW_MS

    private fun constantTimeEquals(a: String, b: String): Boolean {
        // Length-inequality early-exit is safe: the expected HMAC-SHA256 hex length is public
        // (always 64 for this codec), so revealing it leaks no secret. The byte comparison
        // itself runs in constant time via MessageDigest.isEqual.
        if (a.length != b.length) return false
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}
