package app.aaps.implementation.logging

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * That a full log is moved aside rather than thrown away.
 *
 * It used to be truncated: `file.writeText("")` the moment it passed the size bound, which discarded
 * the **whole** history at once. That is the wrong half to lose. A user is asked for logs after
 * something has gone wrong, and the run that went wrong is the run that filled the file - so the
 * evidence was reliably deleted by the act of producing it.
 *
 * A tiny `maxBytes` here so the bound is crossed in a few lines instead of five megabytes.
 */
class AAPSLoggerDesktopRotationTest {

    private val directory: File = Files.createTempDirectory("aaps-log-test").toFile()
    private val logFile = File(directory, "aaps.log")
    private val rotated = File(directory, "aaps.log.1")

    private fun logger(maxBytes: Long = 200L) = AAPSLoggerDesktop(file = logFile, maxBytes = maxBytes)

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a small log is left alone`() {
        logger().error("one line")

        assertTrue(logFile.exists())
        assertTrue(!rotated.exists(), "nothing should have been rotated yet")
    }

    @Test
    fun `crossing the bound moves the old log aside instead of emptying it`() {
        val sut = logger()
        repeat(20) { sut.error("line $it padded out to make the file grow reasonably quickly") }

        assertTrue(rotated.exists(), "the previous log should have been kept as aaps.log.1")
        assertTrue(rotated.length() > 0, "the rotated file must hold the old lines, not be empty")
    }

    /**
     * The whole point: what was written before a rotation is still readable after it.
     *
     * Exactly one rotation, which is what the promise covers. Keeping a single previous file means
     * enough further logging does eventually push old lines out - that is inherent to the design and
     * the same on iOS, not a fault. What must not happen is the old lines vanishing the *instant*
     * the bound is crossed, which is what truncating did.
     */
    @Test
    fun `the old lines survive a rotation`() {
        val sut = logger()
        sut.error("the-incident-line padded out to make the file grow reasonably quickly")
        sut.error("second line padded out to make the file grow reasonably quickly")

        // The append that crosses the bound is the one that rotates, so this third line lands in a
        // fresh file and the two above are moved aside together.
        sut.error("third line padded out to make the file grow reasonably quickly")

        assertTrue(rotated.exists(), "one rotation should have happened by now")
        assertTrue(rotated.readText().contains("the-incident-line"), "the first line was lost")
        assertTrue(!logFile.readText().contains("the-incident-line"), "the live log should have started fresh")
    }

    /** Exactly one previous file, so the worst case on disk stays about twice the bound. */
    @Test
    fun `only one previous log is kept`() {
        val sut = logger()
        repeat(120) { sut.error("line $it padded out to make the file grow reasonably quickly") }

        val logs = directory.listFiles()?.map { it.name }?.sorted().orEmpty()
        assertEquals(listOf("aaps.log", "aaps.log.1"), logs, "rotation should not accumulate files")
    }

    /** Writing continues into a fresh file rather than appending to the rotated one. */
    @Test
    fun `logging carries on after a rotation`() {
        val sut = logger()
        repeat(20) { sut.error("filler $it padded out to make the file grow reasonably quickly") }

        sut.error("after-the-rotation")

        assertTrue(logFile.readText().contains("after-the-rotation"))
    }
}
