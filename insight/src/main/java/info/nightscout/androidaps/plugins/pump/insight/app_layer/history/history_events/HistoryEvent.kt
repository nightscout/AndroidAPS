package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.descriptors.HistoryEvents.Companion.fromId
import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

open class HistoryEvent : Comparable<HistoryEvent> {

    var eventYear = 0
        private set
    var eventMonth = 0
        private set
    var eventDay = 0
        private set
    var eventHour = 0
        private set
    var eventMinute = 0
        private set
    var eventSecond = 0
        private set
    var eventPosition: Long = 0
        private set

    fun parseHeader(byteBuf: ByteBuf) {
        eventYear = BOCUtil.parseBOC(byteBuf.readByte()) * 100 + BOCUtil.parseBOC(byteBuf.readByte())
        eventMonth = BOCUtil.parseBOC(byteBuf.readByte())
        eventDay = BOCUtil.parseBOC(byteBuf.readByte())
        byteBuf.shift(1)
        eventHour = BOCUtil.parseBOC(byteBuf.readByte())
        eventMinute = BOCUtil.parseBOC(byteBuf.readByte())
        eventSecond = BOCUtil.parseBOC(byteBuf.readByte())
        eventPosition = byteBuf.readUInt32LE()
    }

    open fun parse(byteBuf: ByteBuf?) {}
    override fun compareTo(other: HistoryEvent): Int {
        return (eventPosition - other.eventPosition).toInt()
    }

    companion object {
        fun deserialize(byteBuf: ByteBuf): HistoryEvent = fromId(byteBuf.readUInt16LE()).apply {
                parseHeader(byteBuf)
                parse(byteBuf)
            }
    }
}