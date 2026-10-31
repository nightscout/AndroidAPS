package app.aaps.pump.insight.connection_service

import app.aaps.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage
import app.aaps.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage
import app.aaps.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage

/**
 * Several configuration blocks written inside ONE write session.
 *
 * The pump only applies a write session when it is closed, so grouping the writes means a
 * connection lost part way through leaves the pump exactly as it was, instead of applying the
 * blocks that happened to be written before the link died.
 *
 * [ConfigurationMessageRequest] is the single block form of the same thing.
 */
class ConfigurationWriteSessionRequest(
    private val openRequest: MessageRequest<OpenConfigurationWriteSessionMessage>,
    private val writeRequests: List<MessageRequest<WriteConfigurationBlockMessage>>,
    private val closeRequest: MessageRequest<CloseConfigurationWriteSessionMessage>
) {

    /** Waits for the whole session. Throws the first failure, in the order the messages were sent. */
    @Throws(Exception::class)
    fun await() {
        openRequest.await()
        writeRequests.forEach { it.await() }
        closeRequest.await()
    }
}
