package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.utils.ByteBuf

class BasalDeliveryChangedEvent : HistoryEvent() {

    internal var oldBasalRate = 0.0
        private set
    internal var newBasalRate = 0.0
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            oldBasalRate = it.readUInt32Decimal1000()
            newBasalRate = it.readUInt32Decimal1000()
        }
    }
}