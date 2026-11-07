package app.aaps.desktop.shell

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The rule the desktop start up relies on to keep two copies of the app apart.
 *
 * `claimSingleInstance` in `Main.kt` is private and reads the real home directory, so what is
 * checked here is the mechanism it is built on rather than the function itself: an exclusive
 * [FileChannel.tryLock] on a file in the data directory, which returns null for a second holder
 * instead of throwing or waiting.
 *
 * Worth pinning because the failure it prevents is silent. Two instances were found running against
 * one `aaps-desktop.db`, more than half an hour apart, and neither had complained - the window can
 * sit behind another, so starting again from Gradle or the IDE looks like a first start.
 */
class SingleInstanceLockTest {

    private val dataDir: File = Files.createTempDirectory("aaps-lock-test").toFile()
    private val lockFile = File(dataDir, "aaps-desktop.lock")
    private val opened = mutableListOf<FileChannel>()

    @AfterTest
    fun cleanUp() {
        opened.forEach { runCatching { it.close() } }
        dataDir.deleteRecursively()
    }

    @Test
    fun `the first instance gets the lock`() {
        assertNotNull(RandomAccessFile(lockFile, "rw").channel.also { opened += it }.tryLock())
    }

    @Test
    fun `a second instance is refused while the first holds it`() {
        val first = RandomAccessFile(lockFile, "rw").channel.also { opened += it }
        assertNotNull(first.tryLock(), "the first should have taken it")

        val second = RandomAccessFile(lockFile, "rw").channel.also { opened += it }

        // Within one JVM this is OverlappingFileLockException rather than null, which is why the
        // start up catches as well as null-checks. A second process sees null.
        assertNull(runCatching { second.tryLock() }.getOrNull(), "a second holder must be refused")
    }

    /**
     * A closed channel releases the lock, which is what makes a killed instance recoverable.
     *
     * The operating system does this when a process ends, so a crash leaves no stale lock behind
     * that would stop the next start. It is also why `Main.kt` keeps the channel in a field: were it
     * collected, the lock would go with it while the app was still running.
     */
    @Test
    fun `the lock is free again once the holder lets go`() {
        val first = RandomAccessFile(lockFile, "rw").channel
        assertNotNull(first.tryLock())
        first.close()

        assertNotNull(RandomAccessFile(lockFile, "rw").channel.also { opened += it }.tryLock())
    }
}
