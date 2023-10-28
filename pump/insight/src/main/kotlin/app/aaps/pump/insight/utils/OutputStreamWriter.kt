package app.aaps.pump.insight.utils

import app.aaps.core.utils.notifyAll
import app.aaps.core.utils.wait
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

    fun writeAndWait(bytes: ByteArray) {
        synchronized(buffer) {
            buffer.putBytes(bytes)
            buffer.notifyAll()
            try {
                buffer.wait()
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
    }

    init {
        name = javaClass.simpleName
        this.outputStream = outputStream
        this.callback = callback
    }
}