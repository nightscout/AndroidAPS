package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Clearing up old log files on iOS.
 *
 * Against real files, because the whole method is a directory listing and a delete - a fake would
 * only prove the code calls itself. The one rule worth guarding is that the live `aaps.log` is never
 * removed: something is writing to it, and deleting it while it is open loses whatever a user was
 * about to send in.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMaintenanceTest {

    private val manager = NSFileManager.defaultManager
    private val directory = NSTemporaryDirectory() + "aaps-logs-" + NSUUID().UUIDString()

    private val sut = IosMaintenance(SilentLogger(), directory)

    init {
        manager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun removeDirectory() = run { manager.removeItemAtPath(directory, null); Unit }

    private fun createLog(name: String) {
        manager.createFileAtPath("$directory/$name", contents = null, attributes = null)
    }

    private fun remaining(): List<String> {
        @Suppress("UNCHECKED_CAST")
        return (manager.contentsOfDirectoryAtPath(directory, null) as? List<String>).orEmpty().sorted()
    }

    /** The one that matters: something is writing to it. */
    @Test
    fun `the live log is never deleted`() {
        createLog("aaps.log")

        sut.deleteLogs(keep = 1)

        assertTrue(remaining().contains("aaps.log"))
    }

    @Test
    fun `rotated files beyond the limit are deleted`() {
        createLog("aaps.log")
        listOf("aaps.log.1", "aaps.log.2", "aaps.log.3").forEach { createLog(it) }

        sut.deleteLogs(keep = 2)

        assertEquals(listOf("aaps.log", "aaps.log.1"), remaining())
    }

    /**
     * `.1` is the newest, not the oldest. Rotation *moves* the live file onto it, so a higher number
     * is an older file - the opposite of what a plain name sort suggests, and the thing to get right
     * if the logger is ever changed to keep more than one.
     */
    @Test
    fun `the newest rotated file is the one kept`() {
        createLog("aaps.log")
        listOf("aaps.log.1", "aaps.log.2").forEach { createLog(it) }

        sut.deleteLogs(keep = 2)

        assertEquals(listOf("aaps.log", "aaps.log.1"), remaining())
    }

    /** Ordered by the number, so `.10` is older than `.2` rather than sorting between `.1` and `.2`. */
    @Test
    fun `a two digit rotation number is treated as a number`() {
        createLog("aaps.log")
        listOf("aaps.log.1", "aaps.log.2", "aaps.log.10").forEach { createLog(it) }

        sut.deleteLogs(keep = 3)

        assertEquals(listOf("aaps.log", "aaps.log.1", "aaps.log.2"), remaining().sorted())
    }

    /** What the periodic maintenance actually asks for, against what the logger actually keeps. */
    @Test
    fun `the usual settings delete nothing`() {
        createLog("aaps.log")
        createLog("aaps.log.1")

        sut.deleteLogs(keep = 30)

        assertEquals(listOf("aaps.log", "aaps.log.1"), remaining())
    }

    @Test
    fun `an empty directory is not an error`() {
        sut.deleteLogs(keep = 30)

        assertTrue(remaining().isEmpty())
    }

    /** Exports and the database share the directory. Only log files are ours to remove. */
    @Test
    fun `other files are left alone`() {
        createLog("aaps.log")
        createLog("aaps.log.1")
        createLog("2026-09-05_213435_full.json")
        createLog("something.txt")

        sut.deleteLogs(keep = 1)

        assertTrue(remaining().contains("2026-09-05_213435_full.json"))
        assertTrue(remaining().contains("something.txt"))
    }

    /** A build with no Documents directory answers quietly rather than failing. */
    @Test
    fun `no log directory is not an error`() {
        IosMaintenance(SilentLogger(), null).deleteLogs(keep = 30)
    }

    private class SilentLogger : AAPSLogger {
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
}
