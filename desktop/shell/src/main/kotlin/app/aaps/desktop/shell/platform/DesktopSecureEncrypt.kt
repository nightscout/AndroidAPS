package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.utils.hexStringToByteArray
import app.aaps.core.utils.toHex
import app.aaps.desktop.shell.secure.KeyVault
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secrets encrypted with AES-GCM, using keys kept in the desktop [KeyVault].
 *
 * ## Where the keys are
 *
 * In the operating system's key store: DPAPI on Windows, the login Keychain on macOS, the Secret Service
 * on Linux (see `KeyVaults`). A copy of `~/.aaps`, a backup or another account on the computer gets the
 * encrypted secrets but not the keys. Until 2026-10 the keys were plain files next to the data; the
 * first start of a newer version moves them into the key store (`LegacyKeyFiles`).
 *
 * Where no key store exists - Linux without a keyring - the keys stay in files, and the user is told so.
 * No key store protects against a program running as the same user: that is a limit of the desktop.
 *
 * ## The stored format is the same as Android's
 *
 * `<sha256(data)><:><alias><:><ivHex><:><cipherHex>`, where `data` is the three fields after the
 * hash. AES-GCM with a 128 bit tag, exactly as `SecureEncryptImpl` uses, so the shape of a stored
 * secret is identical across platforms. The *keys* are per machine, as they are on Android, so a
 * secret encrypted on one device is not decryptable on another either way.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DesktopSecureEncrypt(
    private val log: AAPSLogger,
    private val cryptoUtil: CryptoUtil,
    private val vault: KeyVault
) : SecureEncrypt {

    /** Keys already read, so the key store is not asked on every call. */
    private val keys = ConcurrentHashMap<String, SecretKeySpec>()

    override fun encrypt(plaintextSecret: String, keystoreAlias: String): String {
        if (plaintextSecret.isEmpty()) {
            log.debug(LTag.CORE, "$MODULE: encrypt() not encrypting empty secret.")
            return ""
        }
        return try {
            val iv = ByteArray(IV_SIZE_BYTE).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, keyFor(keystoreAlias), GCMParameterSpec(TAG_SIZE_BIT, iv))
            }
            val encrypted = cipher.doFinal(plaintextSecret.toByteArray())
            val data = "$keystoreAlias$SEPARATOR${iv.toHex()}$SEPARATOR${encrypted.toHex()}"
            log.info(LTag.CORE, "$MODULE: encrypt() stored encryption secret.")
            "${cryptoUtil.sha256(data)}$SEPARATOR$data"
        } catch (e: Exception) {
            log.error(LTag.CORE, "$MODULE: encrypt failed, msg=${e.message}, $e")
            ""
        }
    }

    override fun decrypt(encryptedSecret: String): String {
        if (encryptedSecret.isEmpty()) {
            log.debug(LTag.CORE, "$MODULE: decrypt() empty not decrypting empty secret.")
            return ""
        }
        if (!isValidDataString(encryptedSecret)) {
            // The hash guards against a truncated or edited file, which would otherwise surface as a
            // decryption exception and read like a key problem.
            log.error(LTag.CORE, "$MODULE: decrypt() data string failed its hash check.")
            return ""
        }
        return try {
            val parts = encryptedSecret.split(SEPARATOR)
            if (parts.size != 4) return ""
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, keyFor(parts[1]), GCMParameterSpec(TAG_SIZE_BIT, parts[2].hexStringToByteArray()))
            }
            String(cipher.doFinal(parts[3].hexStringToByteArray())).also {
                log.info(LTag.CORE, "$MODULE: decrypt() secret decrypted.")
            }
        } catch (e: Exception) {
            log.error(LTag.CORE, "$MODULE: decrypt failed, msg=${e.message}, $e")
            ""
        }
    }

    override fun isValidDataString(data: String?): Boolean {
        if (data.isNullOrEmpty() || data.split(SEPARATOR).size <= 1) return false
        return data.substringBefore(SEPARATOR) == cryptoUtil.sha256(data.substringAfter(SEPARATOR))
    }

    override fun deleteKey(keystoreAlias: String) {
        keys.remove(keystoreAlias)
        if (vault.delete(keystoreAlias)) log.info(LTag.CORE, "$MODULE: deleted key alias=$keystoreAlias")
    }

    /**
     * The key for [alias], created on first use.
     *
     * A key store that does not answer throws here, and the caller logs it as a failed encrypt or decrypt
     * and returns an empty string - the same answer as any other failure, and never a new key in its place.
     */
    private fun keyFor(alias: String): SecretKeySpec =
        keys.computeIfAbsent(alias) {
            val bytes = vault.load(alias) ?: ByteArray(AES_KEY_SIZE_BYTE).also {
                SecureRandom().nextBytes(it)
                vault.store(alias, it)
                log.info(LTag.CORE, "$MODULE: created key alias=$alias in ${vault.name}")
            }
            SecretKeySpec(bytes, "AES")
        }

    companion object {

        private const val MODULE = "ENCRYPT"
        private const val SEPARATOR = ":"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_SIZE_BIT = 128
        private const val IV_SIZE_BYTE = 12
        private const val AES_KEY_SIZE_BYTE = 32
    }
}
