package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which kind of "no" each desktop automation input gives.
 *
 * The two interfaces look alike - both return a nullable - and they do not mean the same thing.
 * `PairedBtDevices.names()` uses null for "not allowed, and the user can grant it", so the trigger
 * editor turns it into a "grant the Connect permission" error. Desktop answered null there, which
 * put that error on the screen every time the editor opened, for a permission the platform does not
 * have. `LastKnownLocation` really does mean "unknown" by null, and must keep saying it.
 *
 * So these are two tests that look like they contradict each other and do not. That is the point of
 * writing them next to each other.
 */
class DesktopAutomationInputsTest {

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

    /**
     * Empty and not null. Null would ask the user for a Bluetooth permission that does not exist on
     * this platform, which is what the trigger editor did before.
     */
    @Test
    fun `there is no paired device list, which is not the same as being refused one`() {
        val names = DesktopPairedBtDevices(SilentLogger).names()

        assertEquals(emptyList<String>(), names)
    }

    /** Null here is correct: zero would read as "you are exactly at the target". */
    @Test
    fun `an unknown location stays unknown`() {
        val location = DesktopLastKnownLocation(SilentLogger)

        assertNull(location.position())
        assertNull(location.distanceTo(50.0, 14.0))
    }

    /** Nothing to grant on a desktop JVM, so the permission screen has nothing to show. */
    @Test
    fun `there are no permission groups to ask for`() {
        assertTrue(DesktopLocationPermissions().groups().isEmpty())
    }
}
