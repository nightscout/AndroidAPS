package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

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

    override fun parse(byteBuf: ByteBuf?) {
        if (byteBuf != null) {
            beforeYear = BOCUtil.parseBOC(byteBuf.readByte()) * 100 + BOCUtil.parseBOC(byteBuf.readByte())
            beforeMonth = BOCUtil.parseBOC(byteBuf.readByte())
            beforeDay = BOCUtil.parseBOC(byteBuf.readByte())
            byteBuf.shift(1)
            beforeHour = BOCUtil.parseBOC(byteBuf.readByte())
            beforeMinute = BOCUtil.parseBOC(byteBuf.readByte())
            beforeSecond = BOCUtil.parseBOC(byteBuf.readByte())
        }
    }
}