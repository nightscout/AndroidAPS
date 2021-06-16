package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.PumpTime
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetDateTimeMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var pumpTime: PumpTime? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        pumpTime = PumpTime().apply {
            byteBuf.let {
                year = it.readUInt16LE()
                month = it.readUInt8().toInt()
                day = it.readUInt8().toInt()
                hour = it.readUInt8().toInt()
                minute = it.readUInt8().toInt()
                second = it.readUInt8().toInt()
            }
        }
    }
}