package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pruning rotated logs, and never touching the live one.
 *
 * `PeriodicMaintenance` calls `deleteLogs` every cycle. It used to answer `notOnDesktopYet`, which
 * logs at **error** - so a desktop left running produced an error an hour about a feature nobody
 * had asked for, which is how people learn to scroll past errors.
 *
 * The numbering is the part worth pinning. `.1` is the *newest* rotated file, because rotation moves
 * the live log onto it, so "keep the newest" means keep the lowest numbers - the opposite of what a
 * name sort suggests.
 */
class DesktopMaintenanceTest {

    private val directory: File = Files.createTempDirectory("aaps-maintenance-test").toFile()
    private val logFile = File(directory, "aaps.log")

    private val sut = DesktopMaintenance(SilentLogger(), logFile)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun writeLogs(vararg names: String) = names.forEach { File(directory, it).writeText("x") }

    private fun remaining() = directory.listFiles()?.map { it.name }?.sorted().orEmpty()

    @Test
    fun `the live log is never deleted`() {
        writeLogs("aaps.log")

        sut.deleteLogs(1)

        assertEquals(listOf("aaps.log"), remaining(), "something is writing to aaps.log")
    }

    @Test
    fun `keeping one leaves the newest rotated file`() {
        writeLogs("aaps.log", "aaps.log.1", "aaps.log.2", "aaps.log.3")

        sut.deleteLogs(2)

        assertEquals(listOf("aaps.log", "aaps.log.1"), remaining())
    }

    /**
     * Ordered by the number, not as text.
     *
     * A plain string sort puts `.10` between `.1` and `.2`, which would delete a newer file and keep
     * an older one. The desktop logger only ever writes `.1` today, so this is guarding the rule
     * rather than a case that occurs - which is the point, since the logger's bound may change.
     */
    @Test
    fun `two digit numbers are ordered as numbers`() {
        writeLogs("aaps.log", "aaps.log.1", "aaps.log.2", "aaps.log.10")

        sut.deleteLogs(3)

        assertEquals(listOf("aaps.log", "aaps.log.1", "aaps.log.2"), remaining())
    }

    /** The folder is the app's, but a stray file in it is not ours to remove. */
    @Test
    fun `files that are not rotated logs are left alone`() {
        writeLogs("aaps.log", "aaps.log.1", "notes.txt", "aaps.log.old", "preferences.properties")

        sut.deleteLogs(1)

        assertEquals(listOf("aaps.log", "notes.txt", "aaps.log.old", "preferences.properties").sorted(), remaining())
    }

    /** A fresh install has nothing rotated, and that is not an error. */
    @Test
    fun `nothing to delete is not a failure`() {
        sut.deleteLogs(3)

        assertTrue(remaining().isEmpty())
    }

    private class SilentLogger : AAPSLogger {

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
}
