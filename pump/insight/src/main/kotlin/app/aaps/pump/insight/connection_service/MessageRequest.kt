package app.aaps.pump.insight.connection_service

import app.aaps.core.utils.wait
import app.aaps.core.utils.waitMillis
import app.aaps.pump.insight.app_layer.AppLayerMessage

open class MessageRequest<T : AppLayerMessage> internal constructor(var request: T) : Comparable<MessageRequest<*>> {

    var response: T? = null
    var exception: Exception? = null

    @Throws(Exception::class)
    open fun await(): T {
        synchronized(this) {
            while (exception == null && response == null) wait()
            exception?.let { e -> throw e }
            return response!!
        }
    }

    @Throws(Exception::class)
    open fun await(timeout: Long): T {
        synchronized(this) {
            while (exception == null && response == null) waitMillis(timeout)
            exception?.let { e -> throw e }
            return response!!
        }
    }

    override fun compareTo(other: MessageRequest<*>): Int {
        return request.compareTo(other.request)
    }
}