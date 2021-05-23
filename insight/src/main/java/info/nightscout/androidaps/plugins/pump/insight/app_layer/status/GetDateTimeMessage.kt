package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.PumpTime
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetDateTimeMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.STATUS) {

    internal var pumpTime: PumpTime? = null
        private set

    override fun parse(byteBuf: ByteBuf?) {
        pumpTime = PumpTime()
        pumpTime?.let {
            byteBuf?.run {
                it.year = readUInt16LE()
                it.month = readUInt8().toInt()
                it.day = readUInt8().toInt()
                it.hour = readUInt8().toInt()
                it.minute = readUInt8().toInt()
                it.second = readUInt8().toInt()
            }
        }
    }
}