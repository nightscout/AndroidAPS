package app.aaps.implementation.protection

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Security.SecItemCopyMatching
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Guards the one thing about [AppleKeychain] a simulator can check: that the Keychain accepts the
 * queries it builds.
 *
 * A round trip cannot be tested here. A test binary has no Keychain of its own, so every call comes
 * back `errSecNotAvailable` (-25291) however well formed the query is - which is why
 * [IosSecureEncrypt] takes a [Keychain] interface and its own test uses a fake.
 *
 * What a test binary *can* tell apart is a malformed query from an unavailable Keychain, because
 * parameter validation happens first. Issue 5185: the queries were built as a Kotlin `Map` and
 * bridged with `CFBridgingRetain`, which boxes the `kSec*` constants instead of passing them through
 * as CFStrings, so every call returned `errSecParam` (-50) and nothing was ever stored. Encryption
 * then generated a fresh key per call, pairing could never complete, and nothing noticed for five
 * weeks.
 *
 * The assertion is on [AppleKeychain.SecQuery], the builder the production methods use, rather than
 * on `store` throwing. Checking the exception instead would prove nothing: the version with the bug
 * ignored the `SecItemAdd` status, so it failed **silently** and a test watching for a thrown
 * message passed against it. That was tried first and it did pass, which is how this ended up here.
 */
@OptIn(ExperimentalForeignApi::class)
class AppleKeychainQueryTest {

    @Test
    fun `the query builder produces something SecItem will parse`() = memScoped {
        val query = AppleKeychain.SecQuery()
        try {
            query.put(kSecClass, kSecClassGenericPassword)
            query.putBridged(kSecAttrService, "app.aaps.secureencrypt.test")
            query.putBridged(kSecAttrAccount, "queryShape")
            query.put(kSecReturnData, kCFBooleanTrue)

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query.dictionary(), result.ptr)

            assertNotEquals(
                ERR_SEC_PARAM, status,
                "SecItem rejected the query as malformed (errSecParam). This is issue 5185: the " +
                    "kSec* constants have to reach SecItem as CFStrings, so the query must be a real " +
                    "CFDictionary - not a Kotlin Map handed to CFBridgingRetain."
            )
        } finally {
            query.close()
        }
    }

    @Test
    fun `load and delete answer instead of throwing`() {
        // load reports "nothing stored" for every failure by design, and delete reports false.
        // Neither may blow up, whatever the Keychain makes of the query.
        val keychain = AppleKeychain(service = "app.aaps.secureencrypt.test")
        assertTrue(runCatching { keychain.load("absent") }.isSuccess, "load threw")
        assertTrue(runCatching { keychain.delete("absent") }.isSuccess, "delete threw")
    }

    private companion object {

        /** `errSecParam` - what SecItem answers when it cannot parse the query at all. */
        const val ERR_SEC_PARAM = -50
    }
}
