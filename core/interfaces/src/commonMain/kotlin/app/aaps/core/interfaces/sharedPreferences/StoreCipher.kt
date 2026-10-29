package app.aaps.core.interfaces.sharedPreferences

/**
 * Encrypts and decrypts the whole preference file of an encrypted [KeyValueStore].
 *
 * Only these two calls and no key, on purpose: the key stays with the platform. On Android that is
 * the only shape that works, because a Keystore key cannot be read out at all - the store has to
 * ask for the work instead of asking for the key. Desktop and iOS follow the same contract.
 */
interface StoreCipher {

    /** Returns [plain] encrypted. The result carries what [decrypt] needs to open it again, such as the IV. */
    fun encrypt(plain: ByteArray): ByteArray

    /**
     * Opens what [encrypt] made.
     *
     * Throws [SealedDataUnreadableException] when the data cannot be opened with this key: there is no
     * key, it is a different key, or the data was changed. Any other exception means the key could not
     * be reached right now, for example a locked keyring, and the data may still be fine.
     */
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * The data cannot be opened with the key this device has: it was made with another key, the key is
 * gone, or the data is damaged. Waiting does not help, unlike a key store that is locked for now.
 */
class SealedDataUnreadableException(message: String, cause: Throwable? = null) : Exception(message, cause)
