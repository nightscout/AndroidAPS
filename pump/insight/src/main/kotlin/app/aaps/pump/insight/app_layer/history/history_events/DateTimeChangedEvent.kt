package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.utils.BOCUtil
import app.aaps.pump.insight.utils.ByteBuf

class DateTimeChangedEvent : HistoryEvent() {

    internal var beforeYear = 0
        private set
    internal var beforeMonth = 0
        private set
    internal var beforeDay = 0
        private set
    internal var beforeHour = 0
        private set
    internal var beforeMinute = 0
        private set
    internal var beforeSecond = 0
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            beforeYear = BOCUtil.parseBOC(it.readByte()) * 100 + BOCUtil.parseBOC(it.readByte())
            beforeMonth = BOCUtil.parseBOC(it.readByte())
            beforeDay = BOCUtil.parseBOC(it.readByte())
            it.shift(1)
            beforeHour = BOCUtil.parseBOC(it.readByte())
            beforeMinute = BOCUtil.parseBOC(it.readByte())
            beforeSecond = BOCUtil.parseBOC(it.readByte())
        }
    }
}