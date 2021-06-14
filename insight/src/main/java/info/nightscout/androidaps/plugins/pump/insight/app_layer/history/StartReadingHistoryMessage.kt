package info.nightscout.androidaps.plugins.pump.insight.app_layer.history

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class StartReadingHistoryMessage : AppLayerMessage(MessagePriority.NORMAL, false, true, Service.HISTORY) {

    internal var offset: Long = 0
    internal var direction: HistoryReadingDirection? = null
    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(8).apply {
                putUInt16LE(31)
                putUInt16LE(direction!!.id)
                putUInt32LE(offset)
            }
            return byteBuf
        }
}