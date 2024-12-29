package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.descriptors.BolusType
import app.aaps.pump.insight.descriptors.BolusType.Companion.fromId
import app.aaps.pump.insight.utils.BOCUtil
import app.aaps.pump.insight.utils.ByteBuf

class BolusDeliveredEvent : HistoryEvent() {

    internal var bolusType: BolusType? = null
        private set
    internal var startHour = 0
        private set
    internal var startMinute = 0
        private set
    internal var startSecond = 0
        private set
    internal var immediateAmount = 0.0
        private set
    internal var extendedAmount = 0.0
        private set
    internal var duration = 0
        private set
    internal var bolusID = 0
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            bolusType = fromId(it.readUInt16LE())
            it.shift(1)
            startHour = BOCUtil.parseBOC(it.readByte())
            startMinute = BOCUtil.parseBOC(it.readByte())
            startSecond = BOCUtil.parseBOC(it.readByte())
            immediateAmount = it.readUInt16Decimal()
            extendedAmount = it.readUInt16Decimal()
            duration = it.readUInt16LE()
            it.shift(2)
            bolusID = it.readUInt16LE()
        }
    }
}