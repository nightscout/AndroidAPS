package app.aaps.implementation.protection

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Where an encryption key is kept.
 *
 * An interface so [IosSecureEncrypt] can be tested without a Keychain: a test binary has no
 * entitlements and Keychain access from one behaves differently from an app, so the cipher would be
 * untestable if it reached the real thing directly.
 */
interface Keychain {

    fun load(alias: String): ByteArray?
    fun store(alias: String, key: ByteArray)

    /** True when something was actually removed. */
    fun delete(alias: String): Boolean
}

/**
 * The real Keychain.
 *
 * Items are stored `AfterFirstUnlockThisDeviceOnly`: never synced to iCloud or restored onto another
 * device, and readable once the phone has been unlocked after boot so background work still runs.
 * A stricter class such as `WhenUnlocked` would stop a backgrounded AAPS reading its own secrets.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
class AppleKeychain(private val service: String = "app.aaps.secureencrypt") : Keychain {

    override fun load(alias: String): ByteArray? = memScoped {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to alias,
            kSecReturnData to true
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query.toCFDictionary(), result.ptr)
        if (status != errSecSuccess) return@memScoped null
        (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
    }

    override fun store(alias: String, key: ByteArray) {
        // Delete first: SecItemAdd fails with errSecDuplicateItem rather than replacing.
        delete(alias)
        val attributes = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to alias,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData to key.toNSData()
        )
        SecItemAdd(attributes.toCFDictionary(), null)
    }

    override fun delete(alias: String): Boolean {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to alias
        )
        return SecItemDelete(query.toCFDictionary()) == errSecSuccess
    }

    private fun Map<Any?, Any?>.toCFDictionary(): CFDictionaryRef? =
        CFBridgingRetain(this as Map<Any?, *>) as? CFDictionaryRef

    // Via base64 rather than raw pointers: the key is 32 bytes, so the copy costs nothing, and
    // memcpy through cinterop is easy to get subtly wrong for no benefit here.
    private fun ByteArray.toNSData(): NSData =
        NSData.create(base64EncodedString = Base64.encode(this), options = 0u) ?: NSData()

    private fun NSData.toByteArray(): ByteArray = Base64.decode(base64EncodedStringWithOptions(0u))
}
