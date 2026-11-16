package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.autotune.AutotuneFolder
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The iOS Autotune folders, against real files.
 *
 * A folder of its own per run, removed afterwards, so nothing is written into the user's Documents.
 * The zip itself is checked by `StoredZipJvmTest`; here only that it is written, and where.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAutotuneFileStoreTest {

    private val manager = NSFileManager.defaultManager
    private val root = NSTemporaryDirectory() + "aaps-autotune-test-" + NSUUID().UUIDString()
    private val sut = IosAutotuneFileStore("$root/autotune")

    @AfterTest
    fun removeDirectory() {
        manager.removeItemAtPath(root, null)
    }

    @Test
    fun aFolderIsMadeOnce() {
        assertTrue(sut.createFolder(AutotuneFolder.RUN))
        assertFalse(sut.createFolder(AutotuneFolder.RUN))
    }

    @Test
    fun aFileHoldsTheTextAndALineBreak() {
        sut.createFolder(AutotuneFolder.SETTINGS)
        assertTrue(sut.write(AutotuneFolder.SETTINGS, "settings.json", "{\"a\":1}"))
        assertEquals("{\"a\":1}\n", NSString.stringWithContentsOfFile("$root/autotune/settings/settings.json", encoding = NSUTF8StringEncoding, error = null))
    }

    @Test
    fun deletingLeavesFoldersInside() {
        sut.createFolder(AutotuneFolder.RUN)
        sut.write(AutotuneFolder.RUN, "old.json", "{}")
        manager.createDirectoryAtPath("$root/autotune/autotune/inner", withIntermediateDirectories = true, attributes = null, error = null)
        sut.deleteFiles(AutotuneFolder.RUN)
        assertEquals(listOf<Any?>("inner"), manager.contentsOfDirectoryAtPath("$root/autotune/autotune", null))
    }

    @Test
    fun theZipIsWrittenNextToTheFolders() {
        AutotuneFolder.entries.forEach { sut.createFolder(it) }
        sut.write(AutotuneFolder.RUN, "aaps-entries.json", "[]")
        sut.write(AutotuneFolder.SETTINGS, "settings.json", "{}")
        assertTrue(sut.zip("autotune_test.zip"))
        val zip = assertNotNull(NSData.dataWithContentsOfFile("$root/autotune/autotune_test.zip"))
        assertTrue(zip.length.toInt() > 22)
    }
}
