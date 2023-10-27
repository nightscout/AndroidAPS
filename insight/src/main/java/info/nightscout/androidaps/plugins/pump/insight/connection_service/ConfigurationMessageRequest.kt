package info.nightscout.androidaps.plugins.pump.insight.connection_service

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage

class ConfigurationMessageRequest<T : AppLayerMessage>(
    request: T,
    private val openRequest: MessageRequest<OpenConfigurationWriteSessionMessage>,
    private val closeRequest: MessageRequest<CloseConfigurationWriteSessionMessage>
    ) : MessageRequest<T>(request) {

    @Throws(Exception::class) override fun await(): T {
        openRequest.await()
        val response = super.await()
        closeRequest.await()
        return response
    }

    @Throws(Exception::class) override fun await(timeout: Long): T {
        openRequest.await(timeout)
        val response = super.await(timeout)
        closeRequest.await(timeout)
        return response
    }
}