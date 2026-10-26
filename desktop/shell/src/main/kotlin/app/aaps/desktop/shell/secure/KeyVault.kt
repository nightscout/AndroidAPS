package app.aaps.desktop.shell.secure

/**
 * Where the desktop keeps its encryption keys: the store's own key and every `SecureEncrypt` key.
 *
 * One per operating system, chosen by [KeyVaults.pick]: DPAPI on Windows, the login Keychain on macOS,
 * the Secret Service (GNOME Keyring, KWallet) on Linux, and [FileKeyVault] where none of these exists.
 *
 * ## What an OS vault protects against, and what it does not
 *
 * A key in an OS vault is not a file that can be copied. A backup of the home folder, a copy of
 * `~/.aaps`, a stolen disk or another user account on the same computer gets the encrypted data
 * but not the key. That is the gap this closes: before, the key was a plain file next to the data.
 *
 * It does not protect against a program running as the same user while that user is logged in. Every
 * OS vault hands its secrets to any process of the owning user, by design. Nothing a desktop app can
 * do changes that.
 */
interface KeyVault {

    /** Shown in the log, so a support case can tell which vault a machine uses. */
    val name: String

    /** False only for [FileKeyVault], where the key lies next to the data it protects. */
    val isOsBacked: Boolean

    /**
     * True when the user may be asked once per stored key, as the macOS Keychain asks after an update of
     * an app that is not signed. [KeyVaults.pick] then keeps all keys in one item ([OneItemKeyVault]).
     */
    val promptsPerItem: Boolean get() = false

    /**
     * The key stored under [alias], or null when there is none.
     *
     * Throws [KeyVaultUnavailableException] when the vault exists but does not answer right now, for
     * example a locked keyring. That is not the same as no key: treating it as one would make a new key
     * and lose everything the old one protected.
     */
    fun load(alias: String): ByteArray?

    /** Stores [key] under [alias], replacing what was there. Throws [KeyVaultUnavailableException] on failure. */
    fun store(alias: String, key: ByteArray)

    /** Removes the key under [alias]. True when something was removed. */
    fun delete(alias: String): Boolean
}

/** The vault exists but did not do what was asked. The data it protects may be fine; try again later. */
class KeyVaultUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
