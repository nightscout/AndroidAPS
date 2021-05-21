package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class BasalDeliveryChangedEvent : HistoryEvent() {

    var oldBasalRate = 0.0
        private set
    var newBasalRate = 0.0
        private set

    override fun parse(byteBuf: ByteBuf?) {
        if (byteBuf != null) {
            oldBasalRate = byteBuf.readUInt32Decimal1000()
            newBasalRate = byteBuf.readUInt32Decimal1000()
        }
    }
}