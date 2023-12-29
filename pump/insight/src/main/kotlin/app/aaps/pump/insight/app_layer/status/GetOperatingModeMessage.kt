package app.aaps.pump.insight.app_layer.status

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.descriptors.OperatingMode
import app.aaps.pump.insight.utils.ByteBuf

class GetOperatingModeMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var operatingMode: OperatingMode? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        operatingMode = byteBuf.let { OperatingMode.fromId(it.readUInt16LE()) }
    }
}