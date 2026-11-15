package app.aaps.ios.shell.config

import app.aaps.core.interfaces.configuration.ExternalOptions
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The options a user turns on by creating a file, on iOS.
 *
 * These used to answer a flat `false`, because iOS had nowhere for a person to put such a file. That
 * stopped being true when the app's Documents directory was exposed to the Files app, and these
 * check the whole gesture the way a user performs it: a file appears in `extra`, and the option is
 * on. Real files in a real directory, because the thing being tested *is* the file lookup.
 */
@OptIn(ExperimentalForeignApi::class)
class IosClientOptionsTest {

    private val manager = NSFileManager.defaultManager
    private val extra = NSTemporaryDirectory() + "aaps-extra-" + NSUUID().UUIDString()

    private val sut = IosClientConfig(extraDir = extra)

    init {
        manager.createDirectoryAtPath(extra, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun removeDirectory() {
        manager.removeItemAtPath(extra, null)
    }

    private fun createMarker(option: ExternalOptions) {
        manager.createFileAtPath("$extra/${option.filename}", contents = null, attributes = null)
    }

    @Test
    fun `an option with no file is off`() {
        assertFalse(sut.isEnabled(ExternalOptions.ENGINEERING_MODE))
    }

    @Test
    fun `an option whose file exists is on`() {
        createMarker(ExternalOptions.ENGINEERING_MODE)

        assertTrue(sut.isEnabled(ExternalOptions.ENGINEERING_MODE))
    }

    /** One marker must not turn on the rest. */
    @Test
    fun `only the option that was asked for is on`() {
        createMarker(ExternalOptions.ENGINEERING_MODE)

        assertTrue(sut.isEnabled(ExternalOptions.ENGINEERING_MODE))
        assertFalse(sut.isEnabled(ExternalOptions.UNFINISHED_MODE))
        assertFalse(sut.isEnabled(ExternalOptions.ENABLE_AUTOTUNE))
    }

    /** The one that used to contradict itself: it answered false while the marker was there. */
    @Test
    fun `engineering mode follows its own marker file`() {
        assertFalse(sut.isEngineeringMode())

        createMarker(ExternalOptions.ENGINEERING_MODE)

        assertTrue(sut.isEngineeringMode())
    }

    /**
     * The user cannot restart the app before the file is seen, so it is read on each call. A value
     * cached at start up would ignore them until the next launch.
     */
    @Test
    fun `a file created while the app runs is noticed`() {
        assertFalse(sut.isEnabled(ExternalOptions.UNFINISHED_MODE))

        createMarker(ExternalOptions.UNFINISHED_MODE)

        assertTrue(sut.isEnabled(ExternalOptions.UNFINISHED_MODE), "the option was not re-read")
    }

    /** Removing the file turns it off again, or an option could never be undone. */
    @Test
    fun `deleting the file turns the option off`() {
        createMarker(ExternalOptions.ENGINEERING_MODE)
        manager.removeItemAtPath("$extra/${ExternalOptions.ENGINEERING_MODE.filename}", null)

        assertFalse(sut.isEnabled(ExternalOptions.ENGINEERING_MODE))
    }

    /** Every option is looked up by its own filename, the same names the other platforms use. */
    @Test
    fun `every option is found under the name it declares`() {
        ExternalOptions.entries.forEach { option ->
            createMarker(option)
            assertTrue(sut.isEnabled(option), "${option.name} was not found as ${option.filename}")
            manager.removeItemAtPath("$extra/${option.filename}", null)
        }
    }

    /** A build with no Documents directory answers off rather than failing. */
    @Test
    fun `no extra directory means every option is off`() {
        val none = IosClientConfig(extraDir = null)

        assertFalse(none.isEnabled(ExternalOptions.ENGINEERING_MODE))
        assertFalse(none.isEngineeringMode())
    }
}
