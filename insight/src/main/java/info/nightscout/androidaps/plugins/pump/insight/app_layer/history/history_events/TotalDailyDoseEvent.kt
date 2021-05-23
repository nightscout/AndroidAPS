package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class TotalDailyDoseEvent : HistoryEvent() {

    internal var basalTotal = 0.0
        private set
    internal var bolusTotal = 0.0
        private set
    internal var totalYear = 0
        private set
    internal var totalMonth = 0
        private set
    internal var totalDay = 0
        private set

    override fun parse(byteBuf: ByteBuf?) {
        if (byteBuf != null) {
            basalTotal = byteBuf.readUInt32Decimal100()
            bolusTotal = byteBuf.readUInt32Decimal100()
            totalYear = BOCUtil.parseBOC(byteBuf.readByte()) * 100 + BOCUtil.parseBOC(byteBuf.readByte())
            totalMonth = BOCUtil.parseBOC(byteBuf.readByte())
            totalDay = BOCUtil.parseBOC(byteBuf.readByte())
        }
    }
}