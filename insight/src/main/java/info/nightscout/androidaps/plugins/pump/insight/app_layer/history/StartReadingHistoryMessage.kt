package info.nightscout.androidaps.plugins.pump.insight.app_layer.history

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class StartReadingHistoryMessage : AppLayerMessage(MessagePriority.NORMAL, false, true, Service.HISTORY) {

    private var offset: Long = 0
    private var direction: HistoryReadingDirection? = null
    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(8)
            byteBuf.putUInt16LE(31)
            byteBuf.putUInt16LE(direction!!.id)
            byteBuf.putUInt32LE(offset)
            return byteBuf
        }

    fun setOffset(offset: Long) {
        this.offset = offset
    }

    fun setDirection(direction: HistoryReadingDirection?) {
        this.direction = direction
    }
}