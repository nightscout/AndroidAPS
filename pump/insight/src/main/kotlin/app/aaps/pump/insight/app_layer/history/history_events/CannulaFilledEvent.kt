package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.utils.ByteBuf

class CannulaFilledEvent : HistoryEvent() {

    internal var amount = 0.0
        private set

    override fun parse(byteBuf: ByteBuf) {
        amount = byteBuf.readUInt16Decimal()
    }
}