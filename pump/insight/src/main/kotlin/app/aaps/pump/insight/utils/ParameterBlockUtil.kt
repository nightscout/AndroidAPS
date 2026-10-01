package app.aaps.pump.insight.utils

import app.aaps.pump.insight.app_layer.ReadParameterBlockMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.app_layer.configuration.WriteConfigurationBlockMessage
import app.aaps.pump.insight.app_layer.parameter_blocks.ParameterBlock
import app.aaps.pump.insight.connection_service.InsightConnectionService

@SuppressWarnings("unchecked")
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

    /**
     * Writes several blocks inside ONE write session, in the order given.
     *
     * Use this whenever the blocks belong together. Calling [writeConfigurationBlock] once per
     * block opens and closes a session each time, so the pump commits them one by one and a lost
     * connection can leave only some of them applied.
     */
    @Throws(Exception::class)
    fun writeConfigurationBlocks(connectionService: InsightConnectionService, vararg parameterBlocks: ParameterBlock) {
        val messages = parameterBlocks.map { parameterBlock ->
            WriteConfigurationBlockMessage().apply { setParameterBlock(parameterBlock) }
        }
        connectionService.requestConfigurationWrites(messages).await()
    }
}