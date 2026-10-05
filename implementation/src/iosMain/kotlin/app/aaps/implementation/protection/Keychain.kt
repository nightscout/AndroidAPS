package app.aaps.implementation.protection

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
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
 *
 * ## Why the queries are built by hand
 *
 * A SecItem query must be a real `CFDictionary` whose keys are the `kSec*` constants, which are
 * `CFStringRef` pointers. Building it as a Kotlin `Map` and bridging the map with
 * `CFBridgingRetain` does not work: the constants are boxed as Kotlin objects on the way in, so
 * every call returns `errSecParam` (-50) and nothing is ever read or written. That is how this
 * class behaved from the day it was written until 2026-10-05, which broke client pairing and the
 * stored export password - see issue 5185. Measured on the simulator: the Kotlin-map form gives
 * -50 while the same query as a `CFDictionary` passes parameter validation.
 *
 * So the dictionary is assembled with [CFDictionaryCreateMutable], the CF constants are passed
 * through untouched (including `kCFBooleanTrue` rather than Kotlin `true`), and only the Kotlin
 * values - the service and account strings, the key bytes - are bridged, then released once the
 * dictionary has retained them.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
class AppleKeychain(private val service: String = "app.aaps.secureencrypt") : Keychain {

    override fun load(alias: String): ByteArray? = memScoped {
        val query = SecQuery()
        try {
            query.put(kSecClass, kSecClassGenericPassword)
            query.putBridged(kSecAttrService, service)
            query.putBridged(kSecAttrAccount, alias)
            query.put(kSecReturnData, kCFBooleanTrue)
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.dictionary(), result.ptr)
            if (status != errSecSuccess) return@memScoped null
            (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
        } finally {
            query.close()
        }
    }

    /**
     * Throws when the Keychain refuses the write.
     *
     * Silence here is what made issue 5185 invisible: `keyFor` asks for a key, gets nothing back,
     * generates a fresh one, fails to store it and carries on, so every call encrypts under a
     * different key. [IosSecureEncrypt] catches this and logs it as a failed encrypt, which is the
     * truth rather than a quiet wrong answer.
     */
    override fun store(alias: String, key: ByteArray) {
        // Delete first: SecItemAdd fails with errSecDuplicateItem rather than replacing.
        delete(alias)
        val attributes = SecQuery()
        val status = try {
            attributes.put(kSecClass, kSecClassGenericPassword)
            attributes.putBridged(kSecAttrService, service)
            attributes.putBridged(kSecAttrAccount, alias)
            attributes.put(kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
            attributes.putBridged(kSecValueData, key.toNSData())
            SecItemAdd(attributes.dictionary(), null)
        } finally {
            attributes.close()
        }
        check(status == errSecSuccess) { "Keychain refused to store $alias, OSStatus $status" }
    }

    override fun delete(alias: String): Boolean {
        val query = SecQuery()
        return try {
            query.put(kSecClass, kSecClassGenericPassword)
            query.putBridged(kSecAttrService, service)
            query.putBridged(kSecAttrAccount, alias)
            SecItemDelete(query.dictionary()) == errSecSuccess
        } finally {
            query.close()
        }
    }

    /**
     * A `CFDictionary` under construction, and the bridged values it owns.
     *
     * [putBridged] hands a Kotlin value to Core Foundation with a +1 retain; the dictionary retains
     * it too, so [close] gives that first reference back. Without it every call would leak a string.
     */
    internal class SecQuery {

        private val dict: CFMutableDictionaryRef? = CFDictionaryCreateMutable(
            kCFAllocatorDefault, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr
        )
        private val bridged = mutableListOf<COpaquePointer?>()

        /** For values that are already Core Foundation objects - the `kSec*` constants, `kCFBooleanTrue`. */
        fun put(key: COpaquePointer?, value: COpaquePointer?) = CFDictionaryAddValue(dict, key, value)

        /** For Kotlin values - strings, `NSData` - which have to cross into Core Foundation first. */
        fun putBridged(key: COpaquePointer?, value: Any) {
            val retained = CFBridgingRetain(value)
            bridged += retained
            CFDictionaryAddValue(dict, key, retained)
        }

        fun dictionary(): CFDictionaryRef? = dict

        fun close() {
            bridged.forEach { CFRelease(it) }
            bridged.clear()
            CFRelease(dict)
        }
    }

    // Via base64 rather than raw pointers: the key is 32 bytes, so the copy costs nothing, and
    // memcpy through cinterop is easy to get subtly wrong for no benefit here.
    private fun ByteArray.toNSData(): NSData =
        NSData.create(base64EncodedString = Base64.encode(this), options = 0u) ?: NSData()

    private fun NSData.toByteArray(): ByteArray = Base64.decode(base64EncodedStringWithOptions(0u))
}
