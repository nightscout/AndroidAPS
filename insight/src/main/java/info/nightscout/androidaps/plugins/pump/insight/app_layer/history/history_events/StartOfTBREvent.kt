package info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class StartOfTBREvent : HistoryEvent() {

    var amount = 0
        private set
    var duration = 0
        private set

    override fun parse(byteBuf: ByteBuf?) {
        amount = byteBuf?.readUInt16LE()    ?: 0
        duration = byteBuf?.readUInt16LE()  ?: 0
    }
}