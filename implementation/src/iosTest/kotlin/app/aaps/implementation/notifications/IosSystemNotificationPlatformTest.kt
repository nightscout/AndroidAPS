package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The identifier round trip, which is the one piece of real logic in the iOS platform.
 *
 * Everything else here hands work to `UNUserNotificationCenter` and cannot be checked without a
 * person swiping a notification away. This part can: a dismissal arrives as the identifier string
 * that was posted, and turning it back into an instance key is what connects the two. Get it wrong
 * and dismissals are silently ignored, because the registry is asked to drop a key that never
 * existed.
 */
class IosSystemNotificationPlatformTest {

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    private val platform = IosSystemNotificationPlatform(SilentLogger)

    @Test
    fun `an instance key survives the round trip`() {
        assertEquals(42, platform.instanceKeyOf(platform.identifier(42)))
    }

    /** Instance keys for the multi-instance ids start at 10000 and climb. */
    @Test
    fun `a large instance key survives the round trip`() {
        assertEquals(10_001, platform.instanceKeyOf(platform.identifier(10_001)))
    }

    /** Another app's notification, or one this class never posted, must not map to a key. */
    @Test
    fun `an identifier without our prefix is not ours`() {
        assertNull(platform.instanceKeyOf("42"))
        assertNull(platform.instanceKeyOf("other-app-42"))
    }

    /** The prefix alone, or a non-numeric tail, is not a key either. */
    @Test
    fun `a malformed identifier is not a key`() {
        assertNull(platform.instanceKeyOf("aaps-"))
        assertNull(platform.instanceKeyOf("aaps-abc"))
    }
}
