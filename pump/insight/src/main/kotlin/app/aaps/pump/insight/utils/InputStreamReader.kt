package app.aaps.pump.insight.utils

import java.io.IOException
import java.io.InputStream

class InputStreamReader(inputStream: InputStream, callback: Callback) : Thread() {

    private val inputStream: InputStream
    private val callback: Callback
    override fun run() {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        try {
            while (!isInterrupted) {
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) callback.onErrorWhileReading(IOException("Stream closed")) else callback.onReceiveBytes(buffer, bytesRead)
            }
        } catch (e: IOException) {
            if (!isInterrupted) callback.onErrorWhileReading(e)
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
            }
        }
    }

    fun close() {
        interrupt()
        try {
            inputStream.close()
        } catch (e: IOException) {
        }
    }

    interface Callback {

        fun onReceiveBytes(buffer: ByteArray, bytesRead: Int)
        fun onErrorWhileReading(e: Exception)
    }

    companion object {

        private const val BUFFER_SIZE = 1024
    }

    init {
        name = javaClass.simpleName
        this.inputStream = inputStream
        this.callback = callback
    }
}