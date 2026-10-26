package app.aaps.implementation.protection

import app.aaps.core.interfaces.sharedPreferences.SealedDataUnreadableException
import app.aaps.core.interfaces.sharedPreferences.StoreCipher
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.random.CryptographyRandom
import platform.Foundation.NSLock
import kotlin.concurrent.Volatile

/**
 * AES-256-GCM for the settings store, with the key kept in the iOS Keychain under [alias].
 *
 * The iOS counterpart of desktop's `VaultStoreCipher`, with the same rules:
 * - The key is read once and then kept in memory, so a save does not ask the Keychain each time.
 * - Only a write makes a key when there is none. A read that finds no key reports the data as
 *   unreadable instead: making a key there would replace one that may only be missing for now, and the
 *   file it protects would then be lost for good.
 * - A Keychain that does not answer is not "no key": [Keychain.loadStrict] throws
 *   [KeychainUnavailableException], and that passes through unchanged.
 *
 * The Keychain items are `AfterFirstUnlockThisDeviceOnly` (see [AppleKeychain]), so the key never
 * leaves this phone, not even in a backup.
 *
 * ```
 * sealed = iv (12 bytes) | ciphertext | GCM tag (16 bytes)
 * ```
 */
@OptIn(DelicateCryptographyApi::class)
class KeychainStoreCipher(private val keychain: Keychain, private val alias: String) : StoreCipher {

    private val aes by lazy { CryptographyProvider.Default.get(AES.GCM) }
    private val lock = NSLock()
    @Volatile private var key: AES.GCM.Key? = null

    override fun encrypt(plain: ByteArray): ByteArray {
        // A fresh IV for every write. CryptographyRandom, not kotlin.random: the IV must not be guessable.
        val iv = CryptographyRandom.nextBytes(IV_BYTES)
        return iv + keyForWrite().cipher().encryptWithIvBlocking(iv, plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val key = key ?: keychain.loadStrict(alias)?.let { decode(it) }?.also { key = it }
            ?: throw SealedDataUnreadableException("There is no key for the settings store in the Keychain")
        if (sealed.size < IV_BYTES + TAG_BYTES) throw SealedDataUnreadableException("The settings store is too short")
        return try {
            key.cipher().decryptWithIvBlocking(sealed.copyOfRange(0, IV_BYTES), sealed.copyOfRange(IV_BYTES, sealed.size))
        } catch (e: Exception) {
            // The GCM tag does not match: another key, or a changed file.
            throw SealedDataUnreadableException("The settings store does not open with the key in the Keychain", e)
        }
    }

    /** Locked, so two first writes cannot each make a key and store it over the other's. */
    private fun keyForWrite(): AES.GCM.Key {
        key?.let { return it }
        lock.lock()
        try {
            return key ?: decode(keychain.loadStrict(alias) ?: CryptographyRandom.nextBytes(KEY_BYTES).also { keychain.store(alias, it) })
                .also { key = it }
        } finally {
            lock.unlock()
        }
    }

    private fun decode(raw: ByteArray): AES.GCM.Key = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, raw)

    private companion object {

        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
