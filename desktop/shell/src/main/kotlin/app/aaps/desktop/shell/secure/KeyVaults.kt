package app.aaps.desktop.shell.secure

import java.io.File
import java.security.SecureRandom
import java.util.UUID

/**
 * Chooses the [KeyVault] for this computer, once per start.
 *
 * ```
 * -Daaps.desktop.keyVault=file       -> FileKeyVault (tests use this, so they never touch a real keyring)
 * Windows / macOS / Linux            -> the OS vault, if it passes a self-test
 *   the native library cannot load   -> FileKeyVault, with a warning
 *   no key store is running (Linux)  -> FileKeyVault, with a warning
 *   anything else fails              -> KeyVaultUnavailableException: the app does not start
 * any other OS                       -> FileKeyVault, with a warning
 * ```
 *
 * The last OS row is the one that matters. A key store that exists but does not answer - a locked
 * keyring, a refused permission - most likely holds the real keys. A new key made in a file meanwhile
 * would compete with them, so the app stops and says why instead.
 *
 * The self-test stores a random value under a random alias, reads it back and deletes it. A fixed alias
 * could clash with another copy of AAPS (another desktop client) testing at the same moment.
 */
object KeyVaults {

    /** System property that forces [FileKeyVault]. Set by the desktop test task. */
    const val VAULT_PROPERTY = "aaps.desktop.keyVault"

    /** The vault, and why it is the weak one when it is [FileKeyVault]. */
    class Choice(val vault: KeyVault, val fallbackReason: String?)

    fun pick(osName: String, keysDir: File, namespace: String, osVault: (String) -> KeyVault? = { osVaultFor(it, keysDir, namespace) }): Choice {
        val fileVault = FileKeyVault(keysDir)
        if (System.getProperty(VAULT_PROPERTY).equals("file", ignoreCase = true))
            return Choice(fileVault, "chosen with -D$VAULT_PROPERTY=file")
        val vault = try {
            osVault(osName)
        } catch (e: LinkageError) {
            return Choice(fileVault, "the key store library could not be loaded: ${e.message}")
        } ?: return Choice(fileVault, "no key store is supported on $osName")
        try {
            selfTest(vault)
        } catch (e: LinkageError) {
            return Choice(fileVault, "${vault.name} could not be loaded: ${e.message}")
        } catch (e: KeyVaultAbsentException) {
            return Choice(fileVault, "no key store is running: ${e.message}")
        } catch (e: KeyVaultUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw KeyVaultUnavailableException("${vault.name} did not work: ${e.message}", e)
        }
        return Choice(vault, null)
    }

    /** The vault for [osName], or null when the desktop has none for it. Throws [LinkageError] when its library is missing. */
    fun osVaultFor(osName: String, keysDir: File, namespace: String): KeyVault? {
        val os = osName.lowercase()
        return when {
            os.contains("win")                         -> WindowsDpapiKeyVault(keysDir)
            os.contains("mac") || os.contains("darwin") -> MacKeychainKeyVault("app.aaps.desktop.$namespace")
            os.contains("nux") || os.contains("nix")    -> LinuxSecretServiceKeyVault(namespace)
            else                                       -> null
        }
    }

    private fun selfTest(vault: KeyVault) {
        val alias = "selftest-${UUID.randomUUID()}"
        val canary = ByteArray(16).also { SecureRandom().nextBytes(it) }
        vault.store(alias, canary)
        try {
            if (!canary.contentEquals(vault.load(alias))) throw KeyVaultUnavailableException("${vault.name} returned something other than what was stored")
        } finally {
            runCatching { vault.delete(alias) }
        }
    }
}
