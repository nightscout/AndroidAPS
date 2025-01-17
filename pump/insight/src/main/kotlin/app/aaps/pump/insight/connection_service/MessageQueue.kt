package app.aaps.pump.insight.connection_service

import app.aaps.core.utils.notifyAll
import app.aaps.pump.insight.app_layer.AppLayerMessage

@SuppressWarnings("unchecked")
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
        if (messageRequests.isNotEmpty()) {
            activeRequest = messageRequests[0]
            messageRequests.removeAt(0)
        }
    }

    fun hasPendingMessages(): Boolean {
        return messageRequests.isNotEmpty()
    }

    fun reset() {
        activeRequest = null
        messageRequests.clear()
    }
}