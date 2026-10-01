package app.aaps.pump.danar

import app.aaps.core.interfaces.pump.rfcomm.RfcommSocket
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MessageHashTableBase
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [SerialIOThread] against a socket that behaves like a Bluetooth one.
 *
 * ## Why the input stream blocks
 *
 * These tests used to hand the thread an empty `ByteArrayInputStream`, whose `read` returns EOF
 * straight away. The thread therefore broke its loop and died within microseconds of being started
 * by its own constructor, and `testThreadLifecycle` then asserted that it was alive - a race the
 * main thread usually won and sometimes did not. It failed one CI gate on a tree whose only changes
 * were iOS files, and passed the next four runs.
 *
 * [BlockingInputStream] is what a real socket does: a read waits for data until the stream is
 * closed. `disconnect()` closes it, which is what ends the thread, so "running" and "stopped" are
 * now states the test puts the thread into rather than moments it hopes to catch.
 *
 * No test here sleeps. Waiting a fixed time for a thread is the same bet in a different shape - too
 * short and it is flaky, too long and every run pays for it.
 */
class SerialIOThreadTest : TestBase() {

    @Mock lateinit var rfCommSocket: RfcommSocket
    @Mock lateinit var hashTable: MessageHashTableBase
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var messageBase: MessageBase

    private lateinit var inputStream: BlockingInputStream
    private lateinit var outputStream: ByteArrayOutputStream
    private var thread: SerialIOThread? = null

    @BeforeEach
    fun setup() {
        inputStream = BlockingInputStream()
        outputStream = ByteArrayOutputStream()

        `when`(rfCommSocket.inputStream).thenReturn(inputStream)
        `when`(rfCommSocket.outputStream).thenReturn(outputStream)
        `when`(rfCommSocket.isConnected).thenReturn(true)
        `when`(hashTable.findMessage(anyInt())).thenReturn(messageBase)
        `when`(messageBase.messageName).thenReturn("TestMessage")
        `when`(messageBase.command).thenReturn(0x0101)
        `when`(messageBase.rawMessageBytes).thenReturn(createValidPacket())
    }

    /** Every test leaves the reader blocked on a read, so every test has to release it. */
    @AfterEach
    fun stopThread() {
        thread?.disconnect("test teardown")
        inputStream.close()
    }

    /** Starts the thread the way production does - the constructor does it - and tracks it. */
    private fun startThread(): SerialIOThread =
        SerialIOThread(aapsLogger, rfCommSocket, hashTable, danaPump).also { thread = it }

    @Test
    fun `the thread runs until it is disconnected`() {
        val thread = startThread()

        // Wait until the reader is actually parked inside read(). Without this the test still races
        // the thread it is testing: the reader may not have reached the loop yet, and disconnect
        // would stop it through the keepRunning flag instead of by closing the stream - so the test
        // would pass without ever exercising the path a real disconnect takes.
        assertThat(inputStream.awaitBlocked()).isTrue()
        assertThat(thread.isAlive).isTrue()

        thread.disconnect("Test lifecycle")

        thread.join(STOP_TIMEOUT_MS)
        // The old test only said "Thread should eventually stop" in a comment. A disconnect that
        // left the reader running would keep a Bluetooth socket open behind a closed connection.
        assertThat(thread.isAlive).isFalse()
    }

    @Test
    fun `disconnect can be called twice without failing`() {
        val thread = startThread()

        thread.disconnect("Test disconnect")
        thread.join(STOP_TIMEOUT_MS)
        thread.disconnect("Test disconnect again")

        assertThat(thread.isAlive).isFalse()
    }

    @Test
    fun `nothing is written when the socket is not connected`() {
        `when`(rfCommSocket.isConnected).thenReturn(false)
        val thread = startThread()

        thread.sendMessage(messageBase)

        // The old test only checked that this did not throw. Writing to a closed socket is the
        // failure worth naming, so assert that nothing was written at all.
        assertThat(outputStream.size()).isEqualTo(0)
    }

    @Test
    fun `the message is written when the socket is connected`() {
        val thread = startThread()

        thread.sendMessage(messageBase)

        assertThat(outputStream.toByteArray()).isEqualTo(createValidPacket())
    }

    /** A minimal valid Dana packet: `0x7E 0x7E [length] [data] [crc] [crc] 0x2E 0x2E`. */
    private fun createValidPacket(): ByteArray = byteArrayOf(
        0x7E.toByte(), 0x7E.toByte(), // Start markers
        0x05.toByte(),                // Length (5 bytes of data)
        0x00.toByte(), 0x00.toByte(), // Padding/data
        0x01.toByte(), 0x01.toByte(), // Command
        0x00.toByte(),                // Data
        0x00.toByte(), 0x00.toByte(), // CRC (simplified)
        0x2E.toByte(), 0x2E.toByte()  // End markers
    )

    /**
     * A stream with no data that waits rather than reporting the end of the stream.
     *
     * This is how a Bluetooth socket behaves: the reader blocks until bytes arrive or the socket is
     * closed, and closing it is what wakes it. Returning EOF on close - rather than throwing - takes
     * `SerialIOThread` down its "read returned -1, breaking loop" path, which is the ordinary way it
     * shuts down.
     *
     * The wait is bounded so that a test which forgets to close cannot hang a CI run for its whole
     * timeout; it fails on its own assertions instead.
     */
    private class BlockingInputStream : InputStream() {

        private val closed = CountDownLatch(1)
        private val blocked = CountDownLatch(1)

        /** True once a reader is waiting inside [read]. */
        fun awaitBlocked(): Boolean = blocked.await(BLOCK_TIMEOUT_S, TimeUnit.SECONDS)

        override fun read(): Int = readAfterClose()

        override fun read(b: ByteArray, off: Int, len: Int): Int = readAfterClose()

        override fun available(): Int = 0

        override fun close() {
            closed.countDown()
        }

        private fun readAfterClose(): Int {
            blocked.countDown()
            closed.await(BLOCK_TIMEOUT_S, TimeUnit.SECONDS)
            return -1
        }
    }

    private companion object {

        const val STOP_TIMEOUT_MS = 5_000L
        const val BLOCK_TIMEOUT_S = 30L
    }
}
