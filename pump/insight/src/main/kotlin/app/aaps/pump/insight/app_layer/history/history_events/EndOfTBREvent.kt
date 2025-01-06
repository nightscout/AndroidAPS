package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.utils.BOCUtil
import app.aaps.pump.insight.utils.ByteBuf

class EndOfTBREvent : HistoryEvent() {

    internal var startHour = 0
        private set
    internal var startMinute = 0
        private set
    internal var startSecond = 0
        private set
    internal var amount = 0
        private set
    internal var duration = 0
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            it.shift(1)
            startHour = BOCUtil.parseBOC(it.readByte())
            startMinute = BOCUtil.parseBOC(it.readByte())
            startSecond = BOCUtil.parseBOC(it.readByte())
            amount = it.readUInt16LE()
            duration = it.readUInt16LE()
        }
    }
}