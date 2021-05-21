package info.nightscout.androidaps.plugins.pump.insight.app_layer

import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.ParameterBlock
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.ids.ParameterBlockIDs
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class ReadParameterBlockMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, null) {

    private var parameterBlockId: Class<out ParameterBlock>? = null
    var parameterBlock: ParameterBlock? = null
        private set

    override val data: ByteBuf
        protected get() {
            val byteBuf = ByteBuf(2)
            byteBuf.putUInt16LE(ParameterBlockIDs.IDS.getID(parameterBlockId))
            return byteBuf
        }

    @Throws(Exception::class) override fun parse(byteBuf: ByteBuf?) {
        val newParameterBlock = ParameterBlockIDs.IDS.getType(byteBuf!!.readUInt16LE()).newInstance()
        byteBuf.shift(2) //Restriction level
        newParameterBlock.parse(byteBuf)
        parameterBlock =newParameterBlock
    }

    fun setParameterBlockId(configurationBlockId: Class<out ParameterBlock>?) {
        parameterBlockId = configurationBlockId
    }
}