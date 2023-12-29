package app.aaps.pump.insight.app_layer.parameter_blocks

import app.aaps.pump.insight.utils.ByteBuf

abstract class ParameterBlock {

    abstract fun parse(byteBuf: ByteBuf)
    abstract val data: ByteBuf?
}