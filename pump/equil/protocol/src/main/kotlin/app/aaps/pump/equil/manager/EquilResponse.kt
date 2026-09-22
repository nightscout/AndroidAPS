package app.aaps.pump.equil.manager

import java.nio.ByteBuffer
import java.util.LinkedList

/**
 *
 */
class EquilResponse(val cmdCreateTime: Long) {

    val send: LinkedList<ByteBuffer> = LinkedList<ByteBuffer>()
    var error_message: String? = null
    var delay: Long = 20

    fun hasError(): Boolean {
        return error_message != null
    }

    fun add(buffer: ByteBuffer) {
        send.add(buffer)
    }

    fun shouldDelay(): Boolean = delay > 0
}


