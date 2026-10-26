package app.aaps.desktop.shell.secure

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Keys as generic passwords in the user's login Keychain: service [service], account = the alias.
 *
 * ## Why the older `SecKeychain*` calls
 *
 * Apple marks them deprecated in favour of `SecItem*`, and they still work on current macOS. They take
 * plain C strings and byte buffers, while `SecItem*` needs CoreFoundation dictionaries built through
 * JNA - more code and more places to get wrong (the iOS side already found out how easily: issue 5185).
 * java-keyring and KSafe use the same calls. Decided 2026-10-26.
 *
 * ## Prompts
 *
 * An item belongs to the program that made it, and macOS asks the user before another program reads
 * it. For an app that is not signed, a new version can count as another program, so a prompt after an
 * update is possible. To be checked on a Mac with a packaged build.
 */
class MacKeychainKeyVault(private val service: String) : KeyVault {

    override val name = "macOS Keychain (login keychain, service $service)"
    override val isOsBacked = true

    // Loaded here and not in a companion object: a failure to load must reach KeyVaults.pick as a
    // LinkageError from this constructor, which is what sends it to the file vault.
    private val security: Security = Native.load("Security", Security::class.java)
    private val coreFoundation: CoreFoundation = Native.load("CoreFoundation", CoreFoundation::class.java)

    private val serviceBytes = service.encodeToByteArray()

    override fun load(alias: String): ByteArray? {
        val account = alias.encodeToByteArray()
        val length = IntByReference()
        val data = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(null, serviceBytes.size, serviceBytes, account.size, account, length, data, null)
        return when (status) {
            ERR_SEC_SUCCESS        -> try {
                data.value.getByteArray(0, length.value)
            } finally {
                security.SecKeychainItemFreeContent(null, data.value)
            }

            ERR_SEC_ITEM_NOT_FOUND -> null
            else                   -> throw KeyVaultUnavailableException("The Keychain did not return the key for $alias (OSStatus $status)")
        }
    }

    override fun store(alias: String, key: ByteArray) {
        // Delete first: adding an item that exists fails with errSecDuplicateItem instead of replacing it.
        delete(alias)
        val account = alias.encodeToByteArray()
        val status = security.SecKeychainAddGenericPassword(null, serviceBytes.size, serviceBytes, account.size, account, key.size, key, null)
        if (status != ERR_SEC_SUCCESS) throw KeyVaultUnavailableException("The Keychain did not store the key for $alias (OSStatus $status)")
    }

    override fun delete(alias: String): Boolean {
        val account = alias.encodeToByteArray()
        val item = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(null, serviceBytes.size, serviceBytes, account.size, account, null, null, item)
        if (status != ERR_SEC_SUCCESS) return false
        return try {
            security.SecKeychainItemDelete(item.value) == ERR_SEC_SUCCESS
        } finally {
            coreFoundation.CFRelease(item.value)
        }
    }

    @Suppress("FunctionName")
    private interface Security : Library {

        fun SecKeychainAddGenericPassword(
            keychain: Pointer?,
            serviceNameLength: Int, serviceName: ByteArray,
            accountNameLength: Int, accountName: ByteArray,
            passwordLength: Int, passwordData: ByteArray,
            itemRef: PointerByReference?
        ): Int

        fun SecKeychainFindGenericPassword(
            keychainOrArray: Pointer?,
            serviceNameLength: Int, serviceName: ByteArray,
            accountNameLength: Int, accountName: ByteArray,
            passwordLength: IntByReference?, passwordData: PointerByReference?,
            itemRef: PointerByReference?
        ): Int

        fun SecKeychainItemDelete(itemRef: Pointer): Int
        fun SecKeychainItemFreeContent(attrList: Pointer?, data: Pointer?): Int
    }

    @Suppress("FunctionName")
    private interface CoreFoundation : Library {

        fun CFRelease(cf: Pointer)
    }

    private companion object {

        const val ERR_SEC_SUCCESS = 0
        const val ERR_SEC_ITEM_NOT_FOUND = -25300
    }
}
