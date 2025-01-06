package app.aaps.pump.insight.app_layer.parameter_blocks

import app.aaps.pump.insight.utils.ByteBuf

class TBROverNotificationBlock : ParameterBlock() {

    internal var isEnabled = false
    private var melody = 0

    override fun parse(byteBuf: ByteBuf) {
        isEnabled = byteBuf.readBoolean()
        melody = byteBuf.readUInt16LE()
    }

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(4)
            byteBuf.putBoolean(isEnabled)
            byteBuf.putUInt16LE(melody)
            return byteBuf
        }
}