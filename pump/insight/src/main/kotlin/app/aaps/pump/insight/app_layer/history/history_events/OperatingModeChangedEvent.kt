package app.aaps.pump.insight.app_layer.history.history_events

import app.aaps.pump.insight.descriptors.OperatingMode
import app.aaps.pump.insight.descriptors.OperatingMode.Companion.fromId
import app.aaps.pump.insight.utils.ByteBuf

class OperatingModeChangedEvent : HistoryEvent() {

    internal var oldValue: OperatingMode? = null
        private set
    internal var newValue: OperatingMode? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        byteBuf.let {
            oldValue = fromId(it.readUInt16LE())
            newValue = fromId(it.readUInt16LE())
        }
    }
}