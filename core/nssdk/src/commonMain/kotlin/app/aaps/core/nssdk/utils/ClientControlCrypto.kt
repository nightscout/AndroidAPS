package app.aaps.core.nssdk.utils

import app.aaps.core.nssdk.localmodel.clientcontrol.AckEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.ProgressEnvelope
import app.aaps.core.nssdk.localmodel.clientcontrol.SignedEnvelope
import app.aaps.core.nssdk.utils.ClientControlCrypto.bytesToHex
import app.aaps.core.nssdk.utils.ClientControlCrypto.hexToBytes
import app.aaps.core.nssdk.utils.ClientControlCrypto.timestampWithinSkew
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Sign / verify primitive for the client-control channel.
 *
 * HMAC-SHA256 over [SignedEnvelope.canonicalString]. Signatures are hex-
 * encoded for prefs-friendly transport. Verification uses constant-time
 * comparison on the byte representations to deny timing oracles.
 *
 * Identifiers and secrets are produced by [CryptographyRandom] / [Uuid]. A 32-byte
 * secret matches HMAC-SHA256's preferred key length and survives birthday
 * bounds for the lifetime of any reasonable pairing.
 *
 * This is shared code rather than JVM code, because a follower on iOS signs the commands it sends,
 * and `javax.crypto` does not exist there. The algorithms are not reimplemented: the provider is
 * each platform's own vetted one - JCA on the JVM, CryptoKit on Apple, OpenSSL 3 elsewhere. What
 * holds them together is `ClientControlCryptoVectorsTest`, whose expected values were produced by
 * the previous `javax.crypto` implementation and which now runs on every target.
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

    private const val SECRET_BYTES = 32
    private const val MAX_TIMESTAMP_SKEW_MS = 5L * 60L * 1000L
    private const val HEX_DIGITS = "0123456789abcdef"

    private val hmacKeyDecoder by lazy {
        CryptographyProvider.Default.get(HMAC).keyDecoder(SHA256)
    }

    fun newSecretBytes(): ByteArray = CryptographyRandom.nextBytes(SECRET_BYTES)

    @OptIn(ExperimentalUuidApi::class)
    fun newClientId(): String = Uuid.random().toString()

    /** Lower-case, zero-padded hex encoding of [bytes] (two chars per byte). Inverse of [hexToBytes]. */
    fun bytesToHex(bytes: ByteArray): String {
        // Built by hand rather than with "%02x".format(): String.format is JVM only.
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(HEX_DIGITS[v ushr 4])
            out.append(HEX_DIGITS[v and 0x0F])
        }
        return out.toString()
    }

    /**
     * Decode a hex string back to bytes. Inverse of [bytesToHex].
     *
     * @throws IllegalArgumentException if [hex] has an odd length or contains any non-hex character.
     */
    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Odd-length hex string" }
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hexDigit(hex[i * 2])
            val lo = hexDigit(hex[i * 2 + 1])
            require(hi >= 0 && lo >= 0) { "Non-hex character in input" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** Replaces `Character.digit(c, 16)`, which is JVM only. Returns -1 for a non-hex character. */
    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else        -> -1
    }

    fun sign(secret: ByteArray, canonical: String): String {
        val key = hmacKeyDecoder.decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, secret)
        return bytesToHex(key.signatureGenerator().generateSignatureBlocking(canonical.encodeToByteArray()))
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
        // (always 64 for this codec), so revealing it leaks no secret.
        if (a.length != b.length) return false
        // Replaces MessageDigest.isEqual, which is JVM only. This is a comparison, not a
        // cryptographic algorithm: accumulate every difference and test once at the end, so the
        // running time does not depend on WHERE the first mismatching byte is.
        val x = a.encodeToByteArray()
        val y = b.encodeToByteArray()
        if (x.size != y.size) return false
        var diff = 0
        for (i in x.indices) diff = diff or (x[i].toInt() xor y[i].toInt())
        return diff == 0
    }
}
