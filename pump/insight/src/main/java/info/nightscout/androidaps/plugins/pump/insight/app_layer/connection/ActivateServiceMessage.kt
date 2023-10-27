package info.nightscout.androidaps.plugins.pump.insight.app_layer.connection

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class ActivateServiceMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.CONNECTION) {

    var serviceID: Byte = 0
    var version: Short = 0
    lateinit var servicePassword: ByteArray
    override fun parse(byteBuf: ByteBuf) {
        serviceID = byteBuf.readByte()
        version = byteBuf.readShort()
    }

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(19).apply {
                putByte(serviceID)
                putShort(version)
                putBytes(servicePassword)
            }
            return byteBuf
        }
}