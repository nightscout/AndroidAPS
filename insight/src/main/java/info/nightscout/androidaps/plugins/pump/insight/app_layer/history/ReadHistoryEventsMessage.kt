package info.nightscout.androidaps.plugins.pump.insight.app_layer.history

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.HistoryEvent
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf
import java.util.*

class ReadHistoryEventsMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.HISTORY) {

    internal var historyEvents: MutableList<HistoryEvent> = mutableListOf()

    @Throws(Exception::class) override fun parse(byteBuf: ByteBuf?) {
        historyEvents = mutableListOf()
        historyEvents.let {
            if (byteBuf != null) {
                byteBuf.shift(2)
                val frameCount = byteBuf.readUInt16LE()
                for (i in 0 until frameCount) {
                    val length = byteBuf.readUInt16LE()
                    HistoryEvent.deserialize(ByteBuf.from(byteBuf.readBytes(length)))?.let { it1 -> it.add(it1) }
                }
            }
        }
    }
}