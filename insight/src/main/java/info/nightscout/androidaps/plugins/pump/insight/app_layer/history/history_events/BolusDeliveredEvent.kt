package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType.Companion.fromId
import info.nightscout.androidaps.plugins.pump.insight.utils.BOCUtil
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class BolusDeliveredEvent : HistoryEvent() {

    var bolusType: BolusType? = null
        private set
    var startHour = 0
        private set
    var startMinute = 0
        private set
    var startSecond = 0
        private set
    var immediateAmount = 0.0
        private set
    var extendedAmount = 0.0
        private set
    var duration = 0
        private set
    var bolusID = 0
        private set

    override fun parse(byteBuf: ByteBuf) {
        bolusType = fromId(byteBuf.readUInt16LE())
        byteBuf.shift(1)
        startHour = BOCUtil.parseBOC(byteBuf.readByte())
        startMinute = BOCUtil.parseBOC(byteBuf.readByte())
        startSecond = BOCUtil.parseBOC(byteBuf.readByte())
        immediateAmount = byteBuf.readUInt16Decimal()
        extendedAmount = byteBuf.readUInt16Decimal()
        duration = byteBuf.readUInt16LE()
        byteBuf.shift(2)
        bolusID = byteBuf.readUInt16LE()
    }
}