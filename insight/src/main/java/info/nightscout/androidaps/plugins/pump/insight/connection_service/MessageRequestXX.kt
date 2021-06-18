package info.nightscout.androidaps.plugins.pump.insight.connection_service

import info.nightscout.androidaps.extensions.wait
import info.nightscout.androidaps.extensions.waitMillis
import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage

// Todo: With this file I cannot build App (Nothing is request #10: var "response: T?" in MessageQueue.kt #14: activeRequest!!.response = response
open class MessageRequestXX<T : AppLayerMessage?> internal constructor(var request: T?) : Comparable<MessageRequest<*>> {

    var response: T? = null
    @JvmField var exception: Exception? = null
    @Throws(Exception::class) open fun await(): T {
        synchronized(this) {
            while (exception == null && response == null) wait()
            if (exception != null) throw exception!!
            return response!!
        }
    }

    @Throws(Exception::class) open fun await(timeout: Long): T {
        synchronized(this) {
            while (exception == null && response == null) waitMillis(timeout)
            if (exception != null) throw exception!!
            return response!!
        }
    }

    override fun compareTo(other: MessageRequest<*>): Int {
        return request!!.compareTo(other.request!!)
    }
}