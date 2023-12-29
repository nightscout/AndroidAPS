package app.aaps.pump.insight.app_layer.parameter_blocks

import app.aaps.pump.insight.descriptors.SystemIdentification
import app.aaps.pump.insight.utils.ByteBuf

class SystemIdentificationBlock : ParameterBlock() {

    var systemIdentification: SystemIdentification? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        systemIdentification = SystemIdentification()
        systemIdentification!!.serialNumber = byteBuf.readUTF16(18)
        systemIdentification!!.systemIdAppendix = byteBuf.readUInt32LE()
        systemIdentification!!.manufacturingDate = byteBuf.readUTF16(22)
    }

    override val data: ByteBuf?
        get() {
            return null
        }
}