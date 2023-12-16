package app.aaps.pump.insight.app_layer.status

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.descriptors.PumpTime
import app.aaps.pump.insight.utils.ByteBuf

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