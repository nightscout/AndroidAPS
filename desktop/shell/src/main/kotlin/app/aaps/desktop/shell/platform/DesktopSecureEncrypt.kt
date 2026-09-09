package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.utils.hexStringToByteArray
import app.aaps.core.utils.toHex
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import app.aaps.implementation.maintenance.DesktopFolders
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secrets encrypted with AES-GCM, using keys kept in files under the AAPS folder.
 *
 * ## What this protects against, and what it does not
 *
 * **Be clear about this before relying on it.** Android puts its key in the hardware-backed KeyStore,
 * where it cannot be read out even by the app; iOS uses the Keychain. Desktop has no comparable store
 * that works the same on Windows, macOS and Linux, so the key sits in a file next to the data it
 * protects, with owner-only permissions where the filesystem supports them.
 *
 * That means it protects against:
 * - another user account on the same machine reading the secrets,
 * - a secret being readable in a backup, an export or a log,
 * - casual inspection of the preferences file.
 *
 * It does **not** protect against anyone who can read the current user's own files - malware running
 * as the user, or someone with the unlocked machine. A key on disk cannot, whatever wraps it: a
 * passphrase baked into the binary is not a secret, and one typed by the user would have to be typed
 * on every launch.
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
class DesktopSecureEncrypt @Inject constructor(
    private val log: AAPSLogger,
    private val cryptoUtil: CryptoUtil
) : SecureEncrypt {

    private val keyDir = File(DesktopFolders.data, "keys")

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
        val file = File(keyDir, keystoreAlias)
        if (file.exists() && file.delete()) log.info(LTag.CORE, "$MODULE: deleted key alias=$keystoreAlias")
    }

    /** The key for [alias], created on first use. */
    private fun keyFor(alias: String): SecretKeySpec {
        val file = File(keyDir, alias)
        if (!file.exists()) {
            keyDir.mkdirs()
            val key = ByteArray(AES_KEY_SIZE_BYTE).also { SecureRandom().nextBytes(it) }
            file.writeText(key.toHex())
            restrictToOwner(file)
            log.info(LTag.CORE, "$MODULE: created key alias=$alias")
        }
        return SecretKeySpec(file.readText().trim().hexStringToByteArray(), "AES")
    }

    /**
     * Owner-only permissions where the filesystem supports them.
     *
     * POSIX first, because it is exact. On Windows the `File` flags are coarser - they clear access
     * for everyone and then restore it for the owner - and they are what is available without a
     * platform-specific ACL call.
     */
    private fun restrictToOwner(file: File) {
        runCatching {
            Files.setPosixFilePermissions(file.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }.onFailure {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
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
