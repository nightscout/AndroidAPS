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

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            bolusType = fromId(it.readUInt16LE())
            immediateAmount = it.readUInt16Decimal()
            extendedAmount = it.readUInt16Decimal()
            duration = it.readUInt16LE()
            it.shift(4)
            bolusID = it.readUInt16LE()
        }
    }
}