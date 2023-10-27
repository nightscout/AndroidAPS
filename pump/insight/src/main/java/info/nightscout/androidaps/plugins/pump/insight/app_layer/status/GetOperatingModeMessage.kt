package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetOperatingModeMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var operatingMode: OperatingMode? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        operatingMode = byteBuf.let { OperatingMode.fromId(it.readUInt16LE()) }
    }
}