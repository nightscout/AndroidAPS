package app.aaps.pump.insight.app_layer.remote_control

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.BolusType
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.utils.ByteBuf

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
            val byteBuf = ByteBuf(22).apply {
                // 805 => Old value with vibration (2.6.1 and earlier), 252 => new value without vibrations for firmware 3.x
                if (disableVibration) putUInt16LE(252) else putUInt16LE(805)
                putUInt16LE(bolusType!!.id)
                putUInt16LE(31)
                putUInt16LE(0)
                putUInt16Decimal(immediateAmount)
                putUInt16Decimal(extendedAmount)
                putUInt16LE(duration)
                putUInt16LE(0)
                putUInt16Decimal(immediateAmount)
                putUInt16Decimal(extendedAmount)
                putUInt16LE(duration)
            }
            return byteBuf
        }

    @Throws(Exception::class) override fun parse(byteBuf: ByteBuf) {
        byteBuf.run { bolusId = readUInt16LE() }
    }

}