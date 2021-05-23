package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class DeliverBolusMessage : AppLayerMessage(MessagePriority.NORMAL, true, true, Service.REMOTE_CONTROL) {

    internal var bolusType: BolusType? = null
    internal var immediateAmount = 0.0
    internal var extendedAmount = 0.0
    internal var duration = 0
    internal var bolusId = 0
        private set
    internal var disableVibration = false

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(22)
            // 805 => Old value with vibration (2.6.1 and earlier), 252 => new value without vibrations for firmware 3.x
            if (disableVibration) byteBuf.putUInt16LE(252) else byteBuf.putUInt16LE(805)
            byteBuf.putUInt16LE(bolusType!!.id)
            byteBuf.putUInt16LE(31)
            byteBuf.putUInt16LE(0)
            byteBuf.putUInt16Decimal(immediateAmount)
            byteBuf.putUInt16Decimal(extendedAmount)
            byteBuf.putUInt16LE(duration)
            byteBuf.putUInt16LE(0)
            byteBuf.putUInt16Decimal(immediateAmount)
            byteBuf.putUInt16Decimal(extendedAmount)
            byteBuf.putUInt16LE(duration)
            return byteBuf
        }

    @Throws(Exception::class) override fun parse(byteBuf: ByteBuf?) {
        byteBuf?.run { bolusId = readUInt16LE() }
    }

}