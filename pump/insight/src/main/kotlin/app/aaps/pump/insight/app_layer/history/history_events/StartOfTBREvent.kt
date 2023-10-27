package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.utils.ByteBuf

class StartOfTBREvent : HistoryEvent() {

    internal var amount = 0
        private set
    internal var duration = 0
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            amount = it.readUInt16LE()
            duration = it.readUInt16LE()
        }
    }
}