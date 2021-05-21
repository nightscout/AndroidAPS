package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class TotalDailyDoseEvent : HistoryEvent() {

    var basalTotal = 0.0
        private set
    var bolusTotal = 0.0
        private set
    var totalYear = 0
        private set
    var totalMonth = 0
        private set
    var totalDay = 0
        private set

    override fun parse(byteBuf: ByteBuf) {
        basalTotal = byteBuf.readUInt32Decimal100()
        bolusTotal = byteBuf.readUInt32Decimal100()
        totalYear = BOCUtil.parseBOC(byteBuf.readByte()) * 100 + BOCUtil.parseBOC(byteBuf.readByte())
        totalMonth = BOCUtil.parseBOC(byteBuf.readByte())
        totalDay = BOCUtil.parseBOC(byteBuf.readByte())
    }
}