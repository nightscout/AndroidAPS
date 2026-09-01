package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.PasswordHasher
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * The iOS half of [PasswordHasher], byte for byte the same as Android's.
 *
 * **This format is stored, so none of it is a free choice.** A master password set on Android and
 * carried to iOS through an export has to keep verifying, so every detail below copies
 * `CryptoUtil` rather than picking what would be nicer:
 *
 * - the stored text is `hmac:<salt>:<mac>`,
 * - the salt is 32 random bytes written as lowercase hex,
 * - the MAC is HMAC-SHA256 over the password's UTF-8 bytes, and the **key is the UTF-8 bytes of the
 *   salt's hex text**, not the 32 raw bytes. That is easy to get wrong in the direction that looks
 *   more correct, and it would silently reject every existing password.
 *
 * A reference hash that does not start with `hmac:` is compared as plain text. That is how a
 * password saved before hashing existed is still accepted, and it matches Android exactly.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosPasswordHasher @Inject constructor(
    private val aapsLogger: AAPSLogger
) : PasswordHasher {

    private val hmac by lazy { CryptographyProvider.Default.get(HMAC) }

    override fun hashPassword(password: String): String {
        // Already hashed. Android returns it untouched and so must this, or hashing twice would
        // store a hash of a hash and lock the user out.
        if (password.startsWith(PREFIX)) return password
        val salt = CryptographyRandom.nextBytes(SALT_BYTES).toHex()
        return "$PREFIX$salt$SEPARATOR${mac(password, salt)}"
    }

    override fun checkPassword(password: String, referenceHash: String): Boolean {
        if (!referenceHash.startsWith(PREFIX)) return password == referenceHash
        val parts = referenceHash.split(SEPARATOR)
        if (parts.size != 3) {
            aapsLogger.error(LTag.CORE, "$MODULE: stored hash is not in the hmac:salt:mac form")
            return false
        }
        return mac(password, parts[1]) == parts[2]
    }

    /** HMAC-SHA256, keyed by the salt's hex **text**. See the class docs before changing this. */
    private fun mac(message: String, saltHex: String): String {
        val key = hmac.keyDecoder(SHA256)
            .decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, saltHex.encodeToByteArray())
        return key.signatureGenerator().generateSignatureBlocking(message.encodeToByteArray()).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> HEX[(byte.toInt() shr 4) and 0xF].toString() + HEX[byte.toInt() and 0xF] }

    companion object {

        private const val MODULE = "IosPasswordHasher"
        private const val PREFIX = "hmac:"
        private const val SEPARATOR = ":"
        private const val SALT_BYTES = 32
        private const val HEX = "0123456789abcdef"
    }
}
