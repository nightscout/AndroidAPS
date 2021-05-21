package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class EndOfTBREvent : HistoryEvent() {

    var startHour = 0
        private set
    var startMinute = 0
        private set
    var startSecond = 0
        private set
    var amount = 0
        private set
    var duration = 0
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.shift(1)
        startHour = BOCUtil.parseBOC(byteBuf.readByte())
        startMinute = BOCUtil.parseBOC(byteBuf.readByte())
        startSecond = BOCUtil.parseBOC(byteBuf.readByte())
        amount = byteBuf.readUInt16LE()
        duration = byteBuf.readUInt16LE()
    }
}