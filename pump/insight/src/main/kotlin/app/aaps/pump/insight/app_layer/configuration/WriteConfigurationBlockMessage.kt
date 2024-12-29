package app.aaps.pump.insight.app_layer.configuration

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.app_layer.parameter_blocks.ParameterBlock
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.descriptors.ParameterBlocks
import app.aaps.pump.insight.utils.ByteBuf

class WriteConfigurationBlockMessage : AppLayerMessage(MessagePriority.NORMAL, false, true, Service.CONFIGURATION) {

    internal var parameterBlock: ParameterBlock? = null
    internal var configurationBlockId: Class<out ParameterBlock?>? = null
        private set

    override val data: ByteBuf
        get() {
            val configBlockData = parameterBlock!!.data
            val data = ByteBuf(4 + configBlockData!!.filledSize).apply {
                putUInt16LE(ParameterBlocks.fromType(parameterBlock!!.javaClass)!!.id)
                putUInt16LE(31)
                putByteBuf(configBlockData)
            }
            return data
        }

    override fun parse(byteBuf: ByteBuf) {
        configurationBlockId = ParameterBlocks.fromId(byteBuf.readUInt16LE())!!.type
    }

    fun setParameterBlock(parameterBlock: ParameterBlock?) {
        this.parameterBlock = parameterBlock
    }
}