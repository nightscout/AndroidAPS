package info.nightscout.androidaps.plugins.pump.insight.connection_service

import info.nightscout.androidaps.extensions.notifyAll
import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import java.util.*

class MessageQueue {

    var activeRequest: MessageRequest<out AppLayerMessage>? = null
    val messageRequests: MutableList<MessageRequest<out AppLayerMessage>> = ArrayList()

    @Suppress("Unchecked_Cast")
    fun completeActiveRequest(response: AppLayerMessage) {
        if (activeRequest == null) return
        (activeRequest as MessageRequest<AppLayerMessage>?)?.let { activeRequest ->
            synchronized(activeRequest) {
                activeRequest.response = response
                activeRequest.notifyAll()
            }
        }
        activeRequest = null
    }

    fun completeActiveRequest(exception: Exception?) {
        if (activeRequest == null) return
        activeRequest?.let { activeRequest ->
            synchronized(activeRequest) {
                activeRequest.exception = exception
                activeRequest.notifyAll()
            }
        }
        activeRequest = null
    }

    fun completePendingRequests(exception: Exception?) {
        for (messageRequest in messageRequests) {
            synchronized(messageRequest) {
                messageRequest.exception = exception
                messageRequest.notifyAll()
            }
        }
        messageRequests.clear()
    }

    fun enqueueRequest(messageRequest: MessageRequest<*>) {
        messageRequests.add(messageRequest)
        messageRequests.sort()
    }

    fun nextRequest() {
        if (messageRequests.size != 0) {
            activeRequest = messageRequests[0]
            messageRequests.removeAt(0)
        }
    }

    fun hasPendingMessages(): Boolean {
        return messageRequests.size != 0
    }

    fun reset() {
        activeRequest = null
        messageRequests.clear()
    }
}