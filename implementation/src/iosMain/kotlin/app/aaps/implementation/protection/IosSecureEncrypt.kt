package app.aaps.implementation.protection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.SecureEncrypt
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random

/**
 * Secrets encrypted with a key kept in the iOS Keychain.
 *
 * The Android implementation puts its AES key in the Android KeyStore, where it is hardware backed
 * and cannot be read out. The Keychain is the counterpart here: the key is stored under the alias,
 * marked `ThisDeviceOnly` so it never reaches iCloud or another device, and readable only after the
 * first unlock so background work still functions.
 *
 * **One honest difference from Android.** The key is protected by the Keychain and by device
 * encryption, but it is not held in the Secure Enclave and is therefore not non-exportable the way
 * the Android TEE key is - the Enclave only holds EC keys, not the AES key wanted here. Wrapping an
 * AES key with an Enclave EC key would close that gap and is a larger change; recorded in
 * `_docs/ios_blockers.md`.
 *
 * ## The stored format
 *
 * Deliberately the same shape as Android's, so a stored string is self describing and
 * [isValidDataString] behaves identically:
 *
 * ```
 * <sha256 of the rest>:<alias>:<iv hex>:<ciphertext hex>
 * ```
 *
 * The ciphertext is **not** portable between devices, on either platform: the key never leaves the
 * device that made it. The shared format is about being able to read the envelope, not the contents.
 */
@OptIn(DelicateCryptographyApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosSecureEncrypt @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val keychain: Keychain = AppleKeychain()
) : SecureEncrypt {

    private val aes by lazy { CryptographyProvider.Default.get(AES.GCM) }
    private val sha by lazy { CryptographyProvider.Default.get(SHA256).hasher() }

    override fun encrypt(plaintextSecret: String, keystoreAlias: String): String {
        if (plaintextSecret.isEmpty()) return ""
        return try {
            val key = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyFor(keystoreAlias))
            // A fresh 12-byte IV per encryption. Reusing one under the same key would leak plaintext
            // relationships, which is the classic way to get GCM wrong.
            val iv = Random.nextBytes(IV_BYTES)
            val cipherText = key.cipher().encryptWithIvBlocking(iv, plaintextSecret.encodeToByteArray())
            val body = "$keystoreAlias$SEPARATOR${iv.toHex()}$SEPARATOR${cipherText.toHex()}"
            "${sha256(body)}$SEPARATOR$body"
        } catch (e: Throwable) {
            aapsLogger.error(LTag.CORE, "$MODULE: encrypt failed for alias=$keystoreAlias: ${e.message}")
            ""
        }
    }

    override fun decrypt(encryptedSecret: String): String {
        if (encryptedSecret.isEmpty()) return ""
        if (!isValidDataString(encryptedSecret)) {
            aapsLogger.error(LTag.CORE, "$MODULE: decrypt refused a string whose header does not match its body")
            return ""
        }
        return try {
            val parts = encryptedSecret.split(SEPARATOR)
            // header, alias, iv, ciphertext
            if (parts.size != 4) return ""
            val key = aes.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyFor(parts[1]))
            val iv = parts[2].fromHex()
            val cipherText = parts[3].fromHex()
            key.cipher().decryptWithIvBlocking(iv, cipherText).decodeToString()
        } catch (e: Throwable) {
            // Also the wrong-key path: GCM's tag check fails rather than returning rubbish.
            aapsLogger.error(LTag.CORE, "$MODULE: decrypt failed: ${e.message}")
            ""
        }
    }

    /** True when the header hash matches the body, which is what tells tampering from corruption. */
    override fun isValidDataString(data: String?): Boolean {
        if (data.isNullOrEmpty()) return false
        val at = data.indexOf(SEPARATOR)
        if (at <= 0) return false
        val header = data.substring(0, at)
        val body = data.substring(at + 1)
        return body.isNotEmpty() && header == sha256(body)
    }

    override fun deleteKey(keystoreAlias: String) {
        if (keychain.delete(keystoreAlias)) {
            aapsLogger.info(LTag.CORE, "$MODULE: deleted Keychain key alias=$keystoreAlias")
        }
    }

    /** The key for an alias, generated and stored the first time it is asked for. */
    private fun keyFor(alias: String): ByteArray =
        keychain.load(alias) ?: Random.nextBytes(KEY_BYTES).also { keychain.store(alias, it) }

    private fun sha256(value: String): String = sha.hashBlocking(value.encodeToByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { b -> HEX[(b.toInt() shr 4) and 0xF].toString() + HEX[b.toInt() and 0xF] }

    private fun String.fromHex(): ByteArray =
        ByteArray(length / 2) { i -> ((HEX.indexOf(this[i * 2]) shl 4) or HEX.indexOf(this[i * 2 + 1])).toByte() }

    private companion object {

        const val MODULE = "ENCRYPT"
        const val SEPARATOR = ":"
        const val IV_BYTES = 12
        const val KEY_BYTES = 32
        const val HEX = "0123456789abcdef"
    }
}
