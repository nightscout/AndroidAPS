package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType.Companion.fromId
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class BolusProgrammedEvent : HistoryEvent() {

    internal var bolusType: BolusType? = null
        private set
    internal var immediateAmount = 0.0
        private set
    internal var extendedAmount = 0.0
        private set
    internal var duration = 0
        private set
    internal var bolusID = 0
        private set

    override fun parse(byteBuf: ByteBuf?) {
        if (byteBuf != null) {
            bolusType = fromId(byteBuf.readUInt16LE())
            immediateAmount = byteBuf.readUInt16Decimal()
            extendedAmount = byteBuf.readUInt16Decimal()
            duration = byteBuf.readUInt16LE()
            byteBuf.shift(4)
            bolusID = byteBuf.readUInt16LE()
        }
    }
}