package app.aaps.pump.omnipod.omnipod5.bledriver.pod.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding

/**
 * Encrypts/decrypts small byte payloads (here, serialized O5 registration data) using an
 * AES-256-GCM key that lives inside the Android Keystore - the key material itself never
 * leaves secure hardware (or the OS's protected keystore on devices without a secure
 * element) and is never present in plaintext in application memory or storage.
 *
 * This replaces storing private key material as plain Gson-serialized `SharedPreferences`
 * text, which is not meaningfully protected against a rooted device or a backup-extraction
 * attack - matching OmnipodKit's own choice to use the iOS Keychain (rather than
 * `UserDefaults`) for exactly this data. `AndroidKeyStore` is Android's equivalent
 * hardware/OS-backed protection.
 *
 * IMPORTANT CAVEAT: `AndroidKeyStore` is a real Android OS keystore provider that only
 * exists on an actual Android device or emulator - it cannot be exercised in a plain JVM
 * test. Every other piece of cryptography built this session was verified against a real
 * running implementation; this class could not be, for that reason. The AES-GCM API usage
 * here follows Android's own documented key-generation and cipher patterns exactly (same
 * shape as Android's official EncryptedSharedPreferences implementation), but the standard
 * caveat that applies to compiled-but-unexecuted code applies doubly here - please verify
 * this actually round-trips correctly on a real device/emulator before relying on it.
 */
/**
 * Narrow encrypt/decrypt contract, extracted purely so [SecureO5RegistrationStorage]'s
 * surrounding logic (serialization, entry merging, install orchestration) can be tested
 * against a fake implementation - [AndroidKeystoreAesCipher] itself can't be exercised
 * outside a real Android device/emulator (see its own class doc).
 */
interface O5RegistrationCipher {
    fun encrypt(plaintext: ByteArray): String
    fun decrypt(encoded: String): ByteArray
}

@ContributesBinding(AppScope::class, binding = binding<O5RegistrationCipher>())
class AndroidKeystoreAesCipher @Inject constructor() : O5RegistrationCipher {

    private val keyAlias: String = DEFAULT_KEY_ALIAS

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts [plaintext] and returns a single base64 string encoding both the
     * Keystore-generated IV and the ciphertext (`base64(ivLength(1 byte) || iv || ciphertext)`),
     * so [decrypt] can recover the IV without needing it passed separately.
     */
    override fun encrypt(plaintext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        require(iv.size <= 0xFF) { "Unexpectedly long IV: ${iv.size} bytes" }
        val combined = byteArrayOf(iv.size.toByte()) + iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    /** Inverse of [encrypt]. Throws if [encoded] is malformed or decryption/auth fails. */
    override fun decrypt(encoded: String): ByteArray {
        val combined = Base64.getDecoder().decode(encoded)
        require(combined.isNotEmpty()) { "Encoded ciphertext is empty" }

        val ivLength = combined[0].toInt() and 0xFF
        require(combined.size > 1 + ivLength) { "Encoded ciphertext is truncated" }
        val iv = combined.copyOfRange(1, 1 + ivLength)
        val ciphertext = combined.copyOfRange(1 + ivLength, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "app.aaps.pump.omnipod.o5_registration_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
