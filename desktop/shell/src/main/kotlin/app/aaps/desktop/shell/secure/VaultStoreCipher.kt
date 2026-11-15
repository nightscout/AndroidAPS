package app.aaps.desktop.shell.secure

import app.aaps.core.interfaces.sharedPreferences.SealedDataUnreadableException
import app.aaps.core.interfaces.sharedPreferences.StoreCipher
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM for the settings store, with the key kept in [vault] under [alias].
 *
 * The key is read once and then kept in memory, so a save does not call the key store each time.
 *
 * Only a write makes a key when there is none. A read that finds no key reports the data as unreadable
 * instead: making a key there would replace one that may only be missing for now, and the file it
 * protects would then be lost for good.
 */
class VaultStoreCipher(private val vault: KeyVault, private val alias: String) : StoreCipher {

    @Volatile private var key: SecretKeySpec? = null

    override fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, keyForWrite(), GCMParameterSpec(TAG_BITS, iv)) }
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val key = key ?: vault.load(alias)?.let { SecretKeySpec(it, ALGORITHM) }?.also { key = it }
            ?: throw SealedDataUnreadableException("There is no key for the settings store in ${vault.name}")
        if (sealed.size < IV_BYTES + TAG_BITS / 8) throw SealedDataUnreadableException("The settings store is too short")
        return try {
            Cipher.getInstance(TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES)) }
                .doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            // The GCM tag does not match: another key, or a changed file.
            throw SealedDataUnreadableException("The settings store does not open with the key in ${vault.name}", e)
        }
    }

    @Synchronized
    private fun keyForWrite(): SecretKeySpec =
        key ?: SecretKeySpec(vault.load(alias) ?: ByteArray(KEY_BYTES).also { random.nextBytes(it); vault.store(alias, it) }, ALGORITHM).also { key = it }

    private companion object {

        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val random = SecureRandom()
    }
}
