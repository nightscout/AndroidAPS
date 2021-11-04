package info.nightscout.androidaps.plugins.pump.insight.utils

import info.nightscout.androidaps.plugins.pump.insight.app_layer.ReadParameterBlockMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.ParameterBlock
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService

object ParameterBlockUtil {

    @Suppress("Unchecked_Cast")
    @Throws(Exception::class)
    fun <T : ParameterBlock?> readParameterBlock(connectionService: InsightConnectionService, service: Service?, parameterBlock: Class<out T>?): T? {
        val readMessage = ReadParameterBlockMessage()
        readMessage.service = service
        readMessage.parameterBlockId = parameterBlock
        return connectionService.requestMessage(readMessage).await().parameterBlock as T?
    }

    @Throws(Exception::class)
    fun writeConfigurationBlock(connectionService: InsightConnectionService, parameterBlock: ParameterBlock?) {
        val writeMessage = WriteConfigurationBlockMessage()
        writeMessage.setParameterBlock(parameterBlock)
        connectionService.requestMessage(writeMessage).await()
    }
}