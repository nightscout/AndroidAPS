package info.nightscout.androidaps.plugins.pump.insight.app_layer.connection

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class ServiceChallengeMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.CONNECTION) {

    var serviceID: Byte = 0
    lateinit var randomData: ByteArray
        private set
    var version: Short = 0
    override fun parse(byteBuf: ByteBuf) {
        randomData = byteBuf.getBytes(16)
    }

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(3).apply {
                putByte(serviceID)
                putShort(version)
            }
            return byteBuf
        }
}