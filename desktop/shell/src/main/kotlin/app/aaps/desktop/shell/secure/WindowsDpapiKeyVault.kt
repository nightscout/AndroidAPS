package app.aaps.desktop.shell.secure

import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import java.io.File

/**
 * Keys protected by Windows DPAPI (`CryptProtectData`, current user scope).
 *
 * DPAPI does not keep anything itself: it turns the key into a blob that only this Windows user on
 * this computer can turn back. The blob is kept in [dir] as `<alias>.dpapi`. Copying it to another
 * account or another computer gives nothing usable.
 *
 * A blob that cannot be turned back is answered as no key, not as an error. It was made by another
 * user or on another computer (a copied folder), or the user's DPAPI keys were lost by a forced password
 * reset - none of which waiting can fix. The settings store then offers to start fresh, and a new key
 * replaces the blob. Locked states do not exist here: DPAPI works whenever the user is logged in.
 */
class WindowsDpapiKeyVault(private val dir: File) : KeyVault {

    override val name = "Windows DPAPI (current user)"
    override val isOsBacked = true

    override fun load(alias: String): ByteArray? {
        val blob = blobFile(alias).takeIf { it.isFile }?.readBytes() ?: return null
        return try {
            Crypt32Util.cryptUnprotectData(blob, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN)
        } catch (_: Exception) {
            null
        }
    }

    override fun store(alias: String, key: ByteArray) =
        try {
            OwnerOnlyFiles.write(blobFile(alias), Crypt32Util.cryptProtectData(key, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN))
        } catch (e: Exception) {
            throw KeyVaultUnavailableException("DPAPI could not protect the key for $alias", e)
        }

    override fun delete(alias: String): Boolean = blobFile(alias).delete()

    private fun blobFile(alias: String) = File(dir, "$alias$BLOB_SUFFIX")

    companion object {

        /** The file ending of a DPAPI blob, so the old plain key files in the same folder can be told apart. */
        const val BLOB_SUFFIX = ".dpapi"
    }
}
