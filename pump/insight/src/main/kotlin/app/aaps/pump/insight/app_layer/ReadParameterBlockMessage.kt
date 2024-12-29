package app.aaps.pump.insight.app_layer

import app.aaps.pump.insight.app_layer.parameter_blocks.ParameterBlock
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.descriptors.ParameterBlocks
import app.aaps.pump.insight.utils.ByteBuf

class ReadParameterBlockMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, null) {

    var parameterBlockId: Class<out ParameterBlock>? = null

    var parameterBlock: ParameterBlock? = null
        private set

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(2)
            ParameterBlocks.fromType(parameterBlockId!!)?.let { byteBuf.putUInt16LE(it.id) }
            return byteBuf
        }

    @Throws(Exception::class) override fun parse(byteBuf: ByteBuf) {
        parameterBlock = ParameterBlocks.fromId(byteBuf.readUInt16LE())?.type?.getDeclaredConstructor()?.newInstance()
        parameterBlock?.let {
            byteBuf.shift(2) //Restriction level
            it.parse(byteBuf)
        }
    }

}