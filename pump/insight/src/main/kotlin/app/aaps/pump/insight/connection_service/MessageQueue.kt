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
        // Highest priority first. MessagePriority is declared NORMAL, HIGHER, HIGHEST, so the
        // natural order is lowest first - and nextRequest() takes index 0. Sorting ascending
        // therefore sent the most urgent message LAST: a CancelBolusMessage (HIGHEST) waited behind
        // every routine status read that was already queued. The sort is stable, so messages of
        // equal priority keep the order they were added in, which the open/write/close
        // configuration batch depends on.
        messageRequests.sortDescending()
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