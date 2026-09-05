package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * That opening the cloud row in Maintenance does not take the app down.
 *
 * It used to. `getCloudDirectoryInfo` called `failNotOnDesktopYet`, which throws, and the call runs
 * on the AWT event thread from `MaintenanceViewModel.showCloudDirectory` - so the whole window died
 * the moment anyone opened it:
 *
 * ```
 * Exception in thread "AWT-EventQueue-0" kotlin.NotImplementedError:
 *   CloudDirectoryManager.getCloudDirectoryInfo is not implemented on desktop yet
 * ```
 *
 * Throwing is right for the calls that helper was written for - a screen that acts on "did the
 * export succeed" must not be handed a guess. This is a read the user only looks at, so the honest
 * answer is the one every other member of this class already gives: not connected.
 */
class DesktopCloudDirectoryManagerTest {

    private val text = object : TextResolver {
        override fun gs(ref: TextRef): String = "text"
        override fun gs(ref: TextRef, vararg args: Any?): String = "text"
        override fun gsNotLocalised(ref: TextRef): String = "text"
        override fun shortTextMode(): Boolean = false
    }

    private val logger = object : AAPSLogger {
        override fun debug(tag: LTag, message: String) {}
        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
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

    private val manager = DesktopCloudDirectoryManager(logger, text)

    @Test
    fun `reading the cloud directory answers instead of throwing`() {
        val info = manager.getCloudDirectoryInfo()

        assertFalse(info.isCloudActive, "desktop has no cloud provider wired up")
        assertFalse(info.hasCredentials)
        assertFalse(info.hasConnectionError, "nothing has been tried, so nothing has failed")
    }

    /**
     * The sheet draws the name, the description and the icon with no null check, so an empty answer
     * would be a blank row rather than a readable "not connected" one.
     */
    @Test
    fun `the row has something to draw`() {
        val info = manager.getCloudDirectoryInfo()

        assertTrue(info.providerDisplayName.isNotEmpty())
        assertTrue(info.providerDescription.isNotEmpty())
        assertTrue(info.cloudPath.isNotEmpty())
    }

    /** No credentials means no status line, the same as a phone that has never signed in. */
    @Test
    fun `there is no authorized status to show`() {
        assertEquals("", manager.getCloudDirectoryInfo().authorizedStatusText)
    }
}
