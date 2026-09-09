package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.ExportPasswordPlatform
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import app.aaps.implementation.maintenance.DesktopFolders
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.Properties

/**
 * Keeps the encrypted export password in a file under the AAPS directory.
 *
 * This replaces a placeholder that reported the feature as unavailable. That was the right answer
 * while desktop had no `SecureEncrypt`: the only place a password could have gone was a plain file,
 * and writing a master password in the clear to save typing is not a trade worth making quietly. Now
 * that `DesktopSecureEncrypt` exists, what lands here is the same AES-GCM envelope Android stores.
 *
 * **The protection is only as good as the key file**, which sits in `~/.aaps/keys` with owner-only
 * permissions. That stops another account on the machine reading it, and keeps the secret out of
 * backups and exports; it does not stop anyone who can already read this user's own files. The same
 * limit is written up on `DesktopSecureEncrypt`, and it applies to everything desktop encrypts.
 *
 * A properties file rather than DataStore, which is Android only. Two values, read and written whole.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopExportPasswordPlatform @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ExportPasswordPlatform {

    private val file = File(DesktopFolders.data, "export-password.properties")

    override fun read(): ExportPasswordPlatform.Stored? {
        if (!file.isFile) return null
        return runCatching {
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            val secret = properties.getProperty(SECRET_KEY).orEmpty()
            if (secret.isEmpty()) null
            else ExportPasswordPlatform.Stored(secret, properties.getProperty(TIMESTAMP_KEY)?.toLongOrNull() ?: 0L)
        }.onFailure {
            // A damaged file must not stop an export starting. Reporting nothing stored asks the
            // user for the password again, which is the safe direction.
            aapsLogger.error(LTag.CORE, "Cannot read the stored export password: ${it.message}")
        }.getOrNull()
    }

    override fun write(secret: String, timestamp: Long) {
        runCatching {
            file.parentFile.mkdirs()
            val properties = Properties().apply {
                setProperty(SECRET_KEY, secret)
                setProperty(TIMESTAMP_KEY, timestamp.toString())
            }
            file.outputStream().use { properties.store(it, "AAPS unattended export password. Encrypted; the key is in keys/.") }
            restrictToOwner(file)
        }.onFailure { aapsLogger.error(LTag.CORE, "Cannot store the export password: ${it.message}") }
    }

    override fun clear() {
        if (file.exists() && !file.delete()) aapsLogger.error(LTag.CORE, "Cannot delete the stored export password")
    }

    /**
     * Never shortened on desktop.
     *
     * The marker files this answers to are an Android developer aid, and there is no desktop
     * engineering build to gate them behind.
     */
    override fun shortenedValidity(): ExportPasswordPlatform.Validity? = null

    /** Owner-only where the filesystem supports it. Same fallback as `DesktopSecureEncrypt`. */
    private fun restrictToOwner(target: File) {
        runCatching {
            Files.setPosixFilePermissions(target.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }.onFailure {
            target.setReadable(false, false)
            target.setWritable(false, false)
            target.setReadable(true, true)
            target.setWritable(true, true)
        }
    }

    private companion object {

        const val SECRET_KEY = "secret"
        const val TIMESTAMP_KEY = "timestamp"
    }
}
