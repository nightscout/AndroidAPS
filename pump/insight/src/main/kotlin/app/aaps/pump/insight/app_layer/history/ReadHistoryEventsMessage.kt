package app.aaps.pump.insight.app_layer.history

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.app_layer.history.history_events.HistoryEvent
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.utils.ByteBuf

class ReadHistoryEventsMessage : AppLayerMessage(MessagePriority.NORMAL, true, false, Service.HISTORY) {

    internal var historyEvents: MutableList<HistoryEvent> = mutableListOf()

    override fun parse(byteBuf: ByteBuf) {
        historyEvents = mutableListOf()
        historyEvents.let {
            byteBuf.let { it1 ->
                it1.shift(2)
                val frameCount = it1.readUInt16LE()
                for (i in 0 until frameCount) {
                    val length = it1.readUInt16LE()
                    it.add(HistoryEvent.deserialize(ByteBuf.from(it1.readBytes(length))))
                }
            }
        }
    }
}