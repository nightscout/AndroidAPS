package app.aaps.desktop.shell.secure

import app.aaps.core.utils.hexStringToByteArray
import app.aaps.core.utils.toHex
import java.io.File

/**
 * Keys as hex files in [dir], readable by the owner only. What the desktop always did, and still the
 * answer where no OS vault exists, such as Linux without a keyring.
 *
 * **Weak, and the user is told so.** The key lies next to the data it protects: anyone who can read the
 * user's files - a backup, a copy of the folder, malware - has both. It still keeps out other accounts on
 * the same computer.
 *
 * The same file names the desktop has always used, so a machine that stays on this vault keeps its keys
 * without any migration.
 */
class FileKeyVault(private val dir: File) : KeyVault {

    override val name = "files in ${dir.path} (no OS key store)"
    override val isOsBacked = false

    override fun load(alias: String): ByteArray? =
        File(dir, alias).takeIf { it.isFile }?.readText()?.trim()?.hexStringToByteArray()

    override fun store(alias: String, key: ByteArray) =
        try {
            OwnerOnlyFiles.write(File(dir, alias), key.toHex().toByteArray())
        } catch (e: Exception) {
            throw KeyVaultUnavailableException("Cannot write the key file for $alias", e)
        }

    override fun delete(alias: String): Boolean = File(dir, alias).delete()
}
