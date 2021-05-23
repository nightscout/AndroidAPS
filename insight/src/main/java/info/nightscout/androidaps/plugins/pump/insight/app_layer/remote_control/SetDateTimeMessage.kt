package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.descriptors.PumpTime
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class SetDateTimeMessage : AppLayerMessage(MessagePriority.NORMAL, false, true, Service.CONFIGURATION) {

    internal var pumpTime: PumpTime? = null
    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(7)
            byteBuf.putUInt16LE(pumpTime!!.year)
            byteBuf.putUInt8(pumpTime!!.month.toShort())
            byteBuf.putUInt8(pumpTime!!.day.toShort())
            byteBuf.putUInt8(pumpTime!!.hour.toShort())
            byteBuf.putUInt8(pumpTime!!.minute.toShort())
            byteBuf.putUInt8(pumpTime!!.second.toShort())
            return byteBuf
        }
}