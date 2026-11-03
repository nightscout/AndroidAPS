package app.aaps.implementation.logging

import app.aaps.core.interfaces.logging.LTag
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What the crash hook in `AapsAppHost` depends on: that the exception which stops the app is written
 * down in full.
 *
 * This is worth pinning because the obvious alternative source is useless. An uncaught Kotlin
 * exception on iOS ends in `abort()`, and the crash report iOS keeps afterwards holds the whole
 * Kotlin stack but **not** the exception type or message - Kotlin prints those to stdout and stderr,
 * which nothing on a phone records. A real crash of this app on 2026-10-25 left 49 readable frames
 * in the system report and no answer at all to "what went wrong".
 *
 * So the hook logs the throwable instead, and the two assumptions underneath it are tested here: that
 * `stackTraceToString` on Kotlin/Native carries the type and message, and that the logger's
 * error-with-throwable overload puts all of it in the file that "Send logs" shares.
 */
@OptIn(ExperimentalForeignApi::class)
class AAPSLoggerIosCrashTest {

    private val fileName = "unit-test-crash.log"

    /** A folder that does not exist yet, to prove the logger makes the one it needs. */
    private val missingFolder = "unit-test-missing-folder"

    private fun documents(): String? =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String

    private fun path(name: String = fileName): String? = documents()?.let { "$it/$name" }

    @AfterTest
    fun removeTheFile() {
        val manager = NSFileManager.defaultManager
        path()?.let { manager.removeItemAtPath(it, null) }
        documents()?.let { manager.removeItemAtPath("$it/$missingFolder", null) }
    }

    /** Thrown and caught, so it carries a real stack rather than a constructed one. */
    private fun thrown(): Throwable = runCatching { error("the pump said no") }.exceptionOrNull()!!

    @Test
    fun `stackTraceToString carries the type and the message that the crash report leaves out`() {
        val text = thrown().stackTraceToString()

        assertTrue(text.contains("IllegalStateException"), "the exception type is missing from: $text")
        assertTrue(text.contains("the pump said no"), "the exception message is missing from: $text")
    }

    @Test
    fun `logging a throwable writes its type and message and stack to the log file`() {
        val logger = AAPSLoggerIos(fileName = fileName)

        logger.error(LTag.CORE, "Uncaught exception, the app is stopping", thrown())

        val written = NSString.stringWithContentsOfFile(path()!!, NSUTF8StringEncoding, null)
        assertTrue(written != null, "nothing was written to $fileName")
        assertTrue(written.contains("Uncaught exception, the app is stopping"), "the hook's own message is missing")
        assertTrue(written.contains("IllegalStateException"), "the exception type is missing")
        assertTrue(written.contains("the pump said no"), "the exception message is missing")
    }

    /**
     * Errors are never gated, so a crash is recorded whatever the log settings say. `LTag.CORE` ships
     * enabled, so this uses a tag that ships disabled to make the point.
     */
    @Test
    fun `a crash is written even on a tag that is switched off`() {
        val logger = AAPSLoggerIos(fileName = fileName)

        logger.error(LTag.NSCLIENT, "Uncaught exception, the app is stopping", thrown())

        val written = NSString.stringWithContentsOfFile(path()!!, NSUTF8StringEncoding, null)
        assertTrue(written != null, "nothing was written to $fileName")
        assertTrue(written.contains("the pump said no"), "a disabled tag swallowed the crash")
    }

    /**
     * The folder has to be made, not assumed.
     *
     * A phone always has a Documents folder, so this never showed up on a device - but a simulator
     * that has just been created does not, `createFileAtPath` fails there without saying so, and
     * every line was dropped while `NSLog` still printed it. That is the worst shape for this bug:
     * the console looks right and the file that "Send logs" shares is empty. It turned the iOS CI
     * red for a day and passed on every machine where the folder already existed.
     */
    @Test
    fun `the log folder is created when it is missing`() {
        val manager = NSFileManager.defaultManager
        val folder = "${documents()!!}/$missingFolder"
        manager.removeItemAtPath(folder, null)
        assertTrue(!manager.fileExistsAtPath(folder), "the folder has to be absent for this to test anything")

        AAPSLoggerIos(fileName = "$missingFolder/crash.log").error(LTag.CORE, "Uncaught exception, the app is stopping", thrown())

        val written = NSString.stringWithContentsOfFile(path("$missingFolder/crash.log")!!, NSUTF8StringEncoding, null)
        assertTrue(written != null, "the folder was not created, so nothing was written")
        assertTrue(written.contains("the pump said no"), "the crash did not reach the file in the new folder")
    }
}
