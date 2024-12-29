package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.utils.BOCUtil
import app.aaps.pump.insight.utils.ByteBuf

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

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            basalTotal = it.readUInt32Decimal100()
            bolusTotal = it.readUInt32Decimal100()
            totalYear = BOCUtil.parseBOC(it.readByte()) * 100 + BOCUtil.parseBOC(it.readByte())
            totalMonth = BOCUtil.parseBOC(it.readByte())
            totalDay = BOCUtil.parseBOC(it.readByte())
        }
    }
}