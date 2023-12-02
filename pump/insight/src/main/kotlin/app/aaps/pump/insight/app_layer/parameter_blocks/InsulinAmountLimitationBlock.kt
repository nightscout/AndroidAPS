package app.aaps.pump.insight.app_layer.parameter_blocks

import app.aaps.pump.insight.utils.ByteBuf

abstract class InsulinAmountLimitationBlock : ParameterBlock() {

    internal var amountLimitation = 0.0
    override fun parse(byteBuf: ByteBuf) {
        amountLimitation = byteBuf.readUInt16Decimal()
    }

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(2)
            byteBuf.putUInt16Decimal(amountLimitation)
            return byteBuf
        }
}