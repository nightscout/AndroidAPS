package app.aaps.core.nssdk.utils

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlin.coroutines.cancellation.CancellationException

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
 *
 * **The wire format is frozen.** Deployed masters and clients exchange these blobs today, so the
 * shape below is a compatibility contract, not a choice: the IV is stored beside the ciphertext
 * rather than prepended to it, and the GCM tag is appended to the ciphertext the way JCE returns it.
 * That is why [encryptWithIvBlocking] is used instead of the ordinary `encryptBlocking`, which would
 * generate its own IV and prepend it. `ClientControlCryptoVectorsTest` pins both.
 */
@OptIn(DelicateCryptographyApi::class)
object ClientControlPairingCrypto {

    /**
     * PBKDF2 round count. It is part of the implicit offer format: changing it makes existing offer
     * docs underivable, surfacing to the user as a (misleading) "wrong PIN". If this is ever changed,
     * bump the offer `schemaVersion` so old offers are rejected cleanly instead of failing as bad PINs.
     */
    private const val KDF_ITERATIONS = 200_000
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val PIN_DIGITS = 8
    private const val PIN_BOUND = 100_000_000

    private val aesKeyDecoder by lazy {
        CryptographyProvider.Default.get(AES.GCM).keyDecoder()
    }

    /** Random 8-digit PIN as a zero-padded decimal string ("00000000" .. "99999999"). */
    fun newPin(): String {
        // nextInt(bound) is uniform over [0, 10^8): it rejection-samples, so the result is unbiased
        // even though 2^31 is not an exact multiple of 10^8. Same property the JVM version relied on.
        val n = CryptographyRandom.nextInt(PIN_BOUND)
        return n.toString().padStart(PIN_DIGITS, '0')
    }

    fun newSalt(): ByteArray = CryptographyRandom.nextBytes(SALT_BYTES)

    fun newIv(): ByteArray = CryptographyRandom.nextBytes(IV_BYTES)

    /**
     * Wrap [plaintext] under a key derived from [pin] and [salt]. Caller is expected to
     * also persist [salt] and [iv] alongside the ciphertext so the client can rebuild
     * the cipher.
     */
    fun wrap(plaintext: ByteArray, pin: String, salt: ByteArray, iv: ByteArray): ByteArray =
        deriveKey(pin, salt).cipher().encryptWithIvBlocking(iv, plaintext)

    /**
     * Try to unwrap [ciphertext] with [pin] / [salt] / [iv]. Returns null on auth-tag
     * mismatch (wrong PIN, tampered blob) or any other cipher failure — callers cannot
     * distinguish "wrong PIN" from "corrupt blob" intentionally.
     *
     * [CancellationException] is rethrown rather than swallowed, so a coroutine cancelled during
     * the ~200ms PBKDF2 grind still unwinds. The JVM version got this by catching only
     * `GeneralSecurityException`; there is no such shared supertype here, so it is explicit.
     */
    fun unwrap(ciphertext: ByteArray, pin: String, salt: ByteArray, iv: ByteArray): ByteArray? = try {
        deriveKey(pin, salt).cipher().decryptWithIvBlocking(iv, ciphertext)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun deriveKey(pin: String, salt: ByteArray): AES.GCM.Key {
        val keyBytes = CryptographyProvider.Default
            .get(PBKDF2)
            .secretDerivation(
                digest = SHA256,
                iterations = KDF_ITERATIONS,
                outputSize = KEY_BYTES.bytes,
                salt = salt
            )
            // The PIN is ASCII digits, so UTF-8 here matches the char[] the JVM PBKDF2 was given.
            // A non-ASCII PIN would not be safe to assume - see the vectors test.
            .deriveSecretToByteArrayBlocking(pin.encodeToByteArray())
        return aesKeyDecoder.decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyBytes)
    }
}
