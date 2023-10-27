package info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

abstract class ParameterBlock {

    abstract fun parse(byteBuf: ByteBuf)
    abstract val data: ByteBuf?
}