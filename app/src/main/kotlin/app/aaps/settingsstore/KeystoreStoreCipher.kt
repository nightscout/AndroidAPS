package app.aaps.settingsstore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.aaps.core.interfaces.sharedPreferences.SealedDataUnreadableException
import app.aaps.core.interfaces.sharedPreferences.StoreCipher
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableEntryException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM for the settings store, with the key in the Android Keystore ([AndroidKeystoreKeys]).
 *
 * The key cannot be read out of the Keystore, not even by this app, which is why the store asks this class
 * to do the work instead of asking for a key. It does not need a fingerprint or a PIN, so the loop can use
 * it in the background, and it is never in a backup. A phone that loses it - a Keystore reset, a broken
 * Keystore after an update - cannot read the settings any more.
 *
 * The same rules as `KeychainStoreCipher` on iOS and `VaultStoreCipher` on desktop:
 * - The key is looked up once and then kept, so a save does not ask the Keystore for it each time.
 * - Only a write makes a key when there is none. A read that finds no key reports the data as unreadable
 *   instead: making a key there would replace one that may only be missing for now.
 * - The Keystore failing is not "no key". It can fail for a moment around boot, so a failure is tried
 *   again a few times before it is passed on.
 *
 * ```
 * sealed = iv (12 bytes) | ciphertext | GCM tag (16 bytes)
 * ```
 * The Keystore chooses the IV itself; it does not accept one from the app.
 */
class KeystoreStoreCipher(private val keys: Keys) : StoreCipher {

    /** Where the key lives: the Android Keystore in the app, memory in a test. */
    interface Keys {

        /** The key, or null when there is none. Throws [SealedDataUnreadableException] for a key that exists but cannot be used. */
        fun load(): SecretKey?

        fun create(): SecretKey

        /** Removes the key. Nothing happens when there is none. */
        fun delete()
    }

    @Volatile private var key: SecretKey? = null

    override fun encrypt(plain: ByteArray): ByteArray = withRetry {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyForWrite())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "The Keystore chose an IV of ${iv.size} bytes" }
        iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.size < IV_BYTES + TAG_BYTES) throw SealedDataUnreadableException("The settings store is too short")
        return withRetry {
            val key = key ?: keys.load()?.also { key = it }
                ?: throw SealedDataUnreadableException("There is no key for the settings store in the Keystore")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            try {
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, sealed, 0, IV_BYTES))
                cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
            } catch (e: AEADBadTagException) {
                throw SealedDataUnreadableException("The settings store does not open with the key in the Keystore", e)
            } catch (e: InvalidKeyException) {
                // The key is there but cannot be used, which waiting does not change.
                throw SealedDataUnreadableException("The Keystore key of the settings store cannot be used", e)
            }
        }
    }

    /** Locked, so two first writes cannot each make a key, the second replacing the first. */
    @Synchronized
    private fun keyForWrite(): SecretKey = key ?: (keys.load() ?: keys.create()).also { key = it }

    private fun <T> withRetry(block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: SealedDataUnreadableException) {
                throw e
            } catch (e: Exception) {
                if (++attempt >= ATTEMPTS) throw e
                Thread.sleep(RETRY_DELAY_MS * attempt)
            }
        }
    }

    private companion object {

        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 100L
    }
}

/**
 * The key of the settings store in the Android Keystore, under [alias].
 *
 * In the trusted environment of the main processor, never in StrongBox: StrongBox is a separate, slow chip
 * made for small data, and the store encrypts the whole settings file on every save.
 */
class AndroidKeystoreKeys(private val alias: String) : KeystoreStoreCipher.Keys {

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun load(): SecretKey? = try {
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    } catch (e: UnrecoverableEntryException) {
        throw SealedDataUnreadableException("The Keystore key of the settings store cannot be loaded", e)
    } catch (e: UnrecoverableKeyException) {
        throw SealedDataUnreadableException("The Keystore key of the settings store cannot be loaded", e)
    }

    override fun create(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    override fun delete() {
        val keyStore = keyStore()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private companion object {

        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
