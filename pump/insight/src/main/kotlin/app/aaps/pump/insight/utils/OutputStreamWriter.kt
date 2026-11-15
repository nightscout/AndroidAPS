package app.aaps.pump.insight.utils

import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.wait
import app.aaps.core.utils.waitMillis
import app.aaps.pump.insight.utils.OutputStreamWriter.Companion.WRITE_AND_WAIT_TIMEOUT_MS
import java.io.IOException
import java.io.OutputStream

class OutputStreamWriter(outputStream: OutputStream, callback: Callback) : Thread() {

    private val outputStream: OutputStream
    private val callback: Callback
    private val buffer = ByteBuf(BUFFER_SIZE)
    override fun run() {
        try {
            while (!isInterrupted) {
                synchronized(buffer) {
                    if (buffer.filledSize != 0) {
                        outputStream.write(buffer.readBytes())
                        outputStream.flush()
                        buffer.notifyAll()
                    }
                    buffer.wait()
                }
            }
        } catch (e: IOException) {
            if (!isInterrupted) callback.onErrorWhileWriting(e)
        } catch (ignored: InterruptedException) {
        } finally {
            try {
                outputStream.close()
            } catch (e: IOException) {
            }
        }
    }

    fun write(bytes: ByteArray) {
        synchronized(buffer) {
            buffer.putBytes(bytes)
            buffer.notifyAll()
        }
    }

    /**
     * Writes [bytes] and waits until the writer thread has flushed them, at most [WRITE_AND_WAIT_TIMEOUT_MS].
     *
     * The wait used to have no limit. If the writer thread had already stopped on an [IOException],
     * nothing called notifyAll() any more and the caller waited forever - and the caller is the
     * synchronized disconnect() of the connection service, so the whole service stalled with it (#5209).
     */
    fun writeAndWait(bytes: ByteArray) {
        synchronized(buffer) {
            buffer.putBytes(bytes)
            buffer.notifyAll()
            try {
                buffer.waitMillis(WRITE_AND_WAIT_TIMEOUT_MS)
            } catch (e: InterruptedException) {
            }
        }
    }

    fun close() {
        interrupt()
        try {
            outputStream.close()
        } catch (e: IOException) {
        }
    }

    interface Callback {

        fun onErrorWhileWriting(e: Exception)
    }

    companion object {

        private const val BUFFER_SIZE = 1024
        private const val WRITE_AND_WAIT_TIMEOUT_MS = 2000L
    }

    init {
        name = javaClass.simpleName
        this.outputStream = outputStream
        this.callback = callback
    }
}