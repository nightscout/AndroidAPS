package app.aaps.core.nssdk.utils

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wrap / unwrap primitive for the PIN-based pairing offer.
 *
 * Master derives a key from the user-visible PIN with PBKDF2-HMAC-SHA256 and wraps the
 * `PairingPayload` JSON under AES-256-GCM. The wrapped blob is published to the NS
 * `settings` collection as a `aaps_clientcontrol_offer_<clientId>` document so a client
 * that knows the PIN — but does NOT have line-of-sight to the master — can fetch it,
 * unwrap, and complete the pairing through the normal `hello` flow.
 *
 * **Why a separate KDF + AEAD layer over the NS transport.** The PIN is only 8 digits
 * (~26 bits) so the wrapping cipher must be slow enough to make offline brute force
 * costly: even if an attacker scrapes the offer doc during the ~2-minute pairing
 * window, 200 000 PBKDF2 rounds × 10⁸ PINs ≈ months of single-GPU work to recover
 * the full 32-byte HMAC secret. AES-GCM's auth tag also gives "wrong PIN" a clean
 * signal — `unwrap` returns null on tag failure without leaking timing.
 *
 * The offer doc is deleted by the master on dismiss / pairing-complete / expiry, so the
 * attack window is bounded by the live window plus whatever NS retains in tombstones.
 */
object ClientControlPairingCrypto {

    private const val KDF_ALGO = "PBKDF2WithHmacSHA256"

    /**
     * PBKDF2 round count. It is part of the implicit offer format: changing it makes existing offer
     * docs underivable, surfacing to the user as a (misleading) "wrong PIN". If this is ever changed,
     * bump the offer `schemaVersion` so old offers are rejected cleanly instead of failing as bad PINs.
     */
    private const val KDF_ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val CIPHER_TRANSFORM = "AES/GCM/NoPadding"
    private const val PIN_DIGITS = 8

    private val secureRandom = SecureRandom()

    /** Random 8-digit PIN as a zero-padded decimal string ("00000000" .. "99999999"). */
    fun newPin(): String {
        // nextInt(100_000_000) is uniform over [0, 10^8): SecureRandom.nextInt(bound) rejection-samples
        // so the result is unbiased even though 2^31 is not an exact multiple of 10^8.
        val n = secureRandom.nextInt(100_000_000)
        return "%0${PIN_DIGITS}d".format(n)
    }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)

    fun newIv(): ByteArray = ByteArray(IV_BYTES).also(secureRandom::nextBytes)

    /**
     * Wrap [plaintext] under a key derived from [pin] and [salt]. Caller is expected to
     * also persist [salt] and [iv] alongside the ciphertext so the client can rebuild
     * the cipher.
     */
    fun wrap(plaintext: ByteArray, pin: String, salt: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pin, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(plaintext)
    }

    /**
     * Try to unwrap [ciphertext] with [pin] / [salt] / [iv]. Returns null on auth-tag
     * mismatch (wrong PIN, tampered blob) or any other cipher failure — callers cannot
     * distinguish "wrong PIN" from "corrupt blob" intentionally.
     *
     * Catches only [GeneralSecurityException] (covers AEADBadTag, InvalidKey, InvalidKeySpec,
     * NoSuchAlgorithm, IllegalBlockSize, BadPadding) so a CancellationException raised on the
     * calling coroutine during the ~200ms PBKDF2 grind propagates — see structured concurrency.
     */
    fun unwrap(ciphertext: ByteArray, pin: String, salt: ByteArray, iv: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(pin, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(ciphertext)
    } catch (_: GeneralSecurityException) {
        null
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, KDF_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(KDF_ALGO)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
